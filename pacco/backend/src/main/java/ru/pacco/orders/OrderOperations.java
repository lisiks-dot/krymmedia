package ru.pacco.orders;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.OrderViews.Audience;
import ru.pacco.orders.internal.DisputeRepository;
import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.shared.BusinessException;

/**
 * Операции после подтверждения продавцом: хаб в Милане (приёмка, упаковка, передача перевозчику),
 * таможня, пункт выдачи, завершение. В рабочей системе часть шагов придёт вебхуками перевозчика и ПВЗ;
 * здесь их выполняет сотрудник с ролью ops.
 */
@Service
public class OrderOperations {

    private static final Logger log = LoggerFactory.getLogger(OrderOperations.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"));

    /** Очереди рабочих мест. */
    public static final Map<String, Set<OrderStatus>> QUEUES = Map.of(
            "sellers", EnumSet.of(OrderStatus.PAID, OrderStatus.CONFIRMED),
            "hub", EnumSet.of(OrderStatus.TO_HUB, OrderStatus.AT_HUB, OrderStatus.CONSOLIDATED),
            "delivery", EnumSet.of(OrderStatus.HANDED_TO_CARRIER, OrderStatus.IN_CUSTOMS, OrderStatus.RELEASED, OrderStatus.AT_PICKUP_POINT),
            "received", EnumSet.of(OrderStatus.RECEIVED));

    public record LineCheck(int lineNo, boolean ok, String note) {
    }

    private final OrderRepository orders;
    private final DisputeRepository disputes;
    private final OrderViews views;
    private final OrdersProperties props;

    public OrderOperations(OrderRepository orders, DisputeRepository disputes, OrderViews views, OrdersProperties props) {
        this.orders = orders;
        this.disputes = disputes;
        this.views = views;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<OrderView> queue(String name) {
        Set<OrderStatus> statuses = QUEUES.get(name);
        if (statuses == null) {
            throw BusinessException.notFound("Очередь");
        }
        return orders.findByStatusInOrderByIdAsc(statuses).stream().map(o -> views.view(o, Audience.STAFF)).toList();
    }

    /** Приёмка на хабе: сверяем каждую строку с заказом и фотографируем. Несовпадение — возврат денег за строку. */
    @Transactional
    public OrderView accept(String number, List<LineCheck> checks) {
        PurchaseOrder o = require(number, OrderStatus.TO_HUB);
        Instant now = Instant.now();
        for (LineCheck c : checks == null ? List.<LineCheck>of() : checks) {
            OrderLine l = line(o, c.lineNo());
            if (l.getState() != LineState.SHIPPED) {
                continue;
            }
            if (c.ok()) {
                l.moveTo(LineState.ACCEPTED);
                if (c.note() != null && !c.note().isBlank()) l.remark(c.note().trim());
            } else {
                String why = c.note() == null || c.note().isBlank() ? "Не совпал с карточкой товара" : c.note().trim();
                l.close(LineState.REJECTED_AT_HUB, why);
                long rub = o.lineRub(l);
                o.refund(rub);
                o.logNotify("Не прошёл проверку: " + l.getTitle(), why + ". Вернём " + rub + " ₽", "hub", now);
            }
        }
        if (o.getLines().stream().anyMatch(l -> l.getState() == LineState.SHIPPED)) {
            throw BusinessException.unprocessable("lines_unchecked", "Отметьте результат проверки по каждому товару");
        }
        if (!o.hasActiveLines() || o.onlyFoodLeft()) {
            o.cancel(o.hasActiveLines() ? "Остались только продукты, их не отправляем отдельно" : "Ни один товар не прошёл проверку на хабе", "hub", now);
            return views.view(o, Audience.STAFF);
        }
        o.moveTo(OrderStatus.AT_HUB, "Принят на хабе в Милане", "Товары сверили с заказом и сфотографировали", "hub", now);
        return views.view(o, Audience.STAFF);
    }

    @Transactional
    public OrderView pack(String number, int weightGrams) {
        PurchaseOrder o = require(number, OrderStatus.AT_HUB);
        if (weightGrams < 50 || weightGrams > 31_500) {
            throw BusinessException.unprocessable("bad_weight", "Вес посылки — от 50 г до 31,5 кг");
        }
        o.packed(weightGrams);
        o.moveTo(OrderStatus.CONSOLIDATED, "Упакован в посылку",
                String.format("Вес %.1f кг", weightGrams / 1000.0).replace('.', ','), "hub", Instant.now());
        return views.view(o, Audience.STAFF);
    }

    @Transactional
    public OrderView handover(String number, String tracking) {
        PurchaseOrder o = require(number, OrderStatus.CONSOLIDATED);
        if (tracking == null || tracking.isBlank()) {
            throw BusinessException.unprocessable("tracking_required", "Укажите трек-номер перевозчика");
        }
        o.handedOver(tracking.trim().toUpperCase());
        o.moveTo(OrderStatus.HANDED_TO_CARRIER, "Передан перевозчику", "Трек-номер " + o.getCarrierTracking(), "hub", Instant.now());
        return views.view(o, Audience.STAFF);
    }

    @Transactional
    public OrderView customs(String number) {
        PurchaseOrder o = require(number, OrderStatus.HANDED_TO_CARRIER);
        o.moveTo(OrderStatus.IN_CUSTOMS, "Прибыл на таможню", "Таможенный представитель подал декларацию", "customs", Instant.now());
        return views.view(o, Audience.STAFF);
    }

    @Transactional
    public OrderView release(String number) {
        PurchaseOrder o = require(number, OrderStatus.IN_CUSTOMS);
        o.moveTo(OrderStatus.RELEASED, "Выпущен таможней",
                o.getDutyRub() > 0 ? "Пошлину " + o.getDutyRub() + " ₽ оплатили при заказе" : "Без пошлины", "customs", Instant.now());
        return views.view(o, Audience.STAFF);
    }

    /** Посылка в ПВЗ: выдаём код получения и считаем срок хранения. */
    @Transactional
    public OrderView arrive(String number) {
        PurchaseOrder o = require(number, OrderStatus.RELEASED);
        LocalDate until = LocalDate.now(MOSCOW).plusDays(props.storageDays());
        o.arrived(String.format("%06d", RANDOM.nextInt(1_000_000)), until);
        o.moveTo(OrderStatus.AT_PICKUP_POINT, "Ждёт в пункте выдачи", o.getPickupPoint() + ". Хранится до " + DAY.format(until), "pickup", Instant.now());
        return views.view(o, Audience.STAFF);
    }

    /** Выдача в ПВЗ по коду из приложения или СМС. */
    @Transactional
    public OrderView issue(String number, String code) {
        PurchaseOrder o = require(number, OrderStatus.AT_PICKUP_POINT);
        if (code == null || !code.replaceAll("\\D", "").equals(o.getPickupCode())) {
            throw BusinessException.unprocessable("bad_code", "Код получения не совпал");
        }
        Instant now = Instant.now();
        o.received(now);
        o.moveTo(OrderStatus.RECEIVED, "Получен",
                "Если с товаром что-то не так, откройте спор до " + DAY.format(views.disputeUntil(o).atZone(MOSCOW)), "pickup", now);
        return views.view(o, Audience.STAFF);
    }

    /** Покупатель не забрал посылку: возвращаем стоимость товаров, доставку и пошлину — нет. */
    @Transactional
    public OrderView unclaimed(String number) {
        PurchaseOrder o = require(number, OrderStatus.AT_PICKUP_POINT);
        Instant now = Instant.now();
        long goods = o.getLines().stream().filter(OrderLine::isActive).mapToLong(o::lineRub).sum();
        o.getLines().stream().filter(OrderLine::isActive).forEach(l -> l.close(LineState.DECLINED, "Посылку не забрали"));
        o.refund(goods);
        o.moveTo(OrderStatus.CANCELLED, "Посылку не забрали из пункта выдачи",
                "Вернём стоимость товаров, " + goods + " ₽. Доставка и пошлина не возвращаются", "pickup", now);
        return views.view(o, Audience.STAFF);
    }

    /** Завершение вручную (поддержка) — например, покупатель подтвердил, что всё в порядке. */
    @Transactional
    public OrderView complete(String number) {
        PurchaseOrder o = require(number, OrderStatus.RECEIVED);
        if (disputes.existsByOrderIdAndStatus(o.getId(), DisputeStatus.OPEN)) {
            throw BusinessException.unprocessable("dispute_open", "Сначала решите спор по заказу");
        }
        o.moveTo(OrderStatus.COMPLETED, "Заказ завершён", "Продавцы получат деньги в ближайшем реестре выплат", "support", Instant.now());
        return views.view(o, Audience.STAFF);
    }

    /** Раз в час: закрываем заказы, по которым прошло окно возврата и нет открытого спора. */
    @Scheduled(cron = "${pacco.orders.complete-cron:0 7 * * * *}")
    @Transactional
    public int completeExpired() {
        Instant edge = Instant.now().minus(props.returnWindowDays(), ChronoUnit.DAYS);
        int done = 0;
        for (PurchaseOrder o : orders.findByStatusAndReceivedAtBefore(OrderStatus.RECEIVED, edge)) {
            if (disputes.existsByOrderIdAndStatus(o.getId(), DisputeStatus.OPEN)) {
                continue;
            }
            o.moveTo(OrderStatus.COMPLETED, "Заказ завершён", "Срок возврата истёк", "system", Instant.now());
            done++;
        }
        if (done > 0) {
            log.info("Завершено заказов после окна возврата: {}", done);
        }
        return done;
    }

    private PurchaseOrder require(String number, OrderStatus expected) {
        PurchaseOrder o = orders.findByNumber(number).orElseThrow(() -> BusinessException.notFound("Заказ"));
        if (o.getStatus() != expected) {
            throw BusinessException.unprocessable("wrong_status",
                    "Заказ в статусе «" + o.getStatus().label() + "», а для этого шага нужен «" + expected.label() + "»");
        }
        return o;
    }

    private static OrderLine line(PurchaseOrder o, int n) {
        if (n < 0 || n >= o.getLines().size()) {
            throw BusinessException.unprocessable("bad_line", "Нет такой строки в заказе");
        }
        return o.getLines().get(n);
    }
}
