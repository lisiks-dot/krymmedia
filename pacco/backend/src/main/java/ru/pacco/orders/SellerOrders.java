package ru.pacco.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.shared.BusinessException;

/**
 * Заказы с точки зрения продавца: только его строки. Продавец подтверждает или отказывает по каждой строке,
 * затем отправляет товары на хаб с трек-номером. Деньги — за строки, которые дошли до покупателя.
 */
@Service
public class SellerOrders {

    /** Заказы, по которым продавец получит деньги в ближайшем реестре. */
    private static final Set<OrderStatus> PAYABLE = Set.of(OrderStatus.COMPLETED);
    private static final Set<OrderStatus> IN_PROGRESS = Set.of(OrderStatus.PAID, OrderStatus.CONFIRMED,
            OrderStatus.TO_HUB, OrderStatus.AT_HUB, OrderStatus.CONSOLIDATED, OrderStatus.HANDED_TO_CARRIER,
            OrderStatus.IN_CUSTOMS, OrderStatus.RELEASED, OrderStatus.AT_PICKUP_POINT, OrderStatus.RECEIVED);

    private final OrderRepository orders;
    private final CommissionProperties commission;
    private final OrdersProperties props;

    public SellerOrders(OrderRepository orders, CommissionProperties commission, OrdersProperties props) {
        this.orders = orders;
        this.commission = commission;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<View> list(long sellerId) {
        return orders.findBySeller(sellerId).stream()
                .filter(o -> o.getStatus() != OrderStatus.CREATED)
                .map(o -> view(o, sellerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public View get(String number, long sellerId) {
        return view(owned(number, sellerId), sellerId);
    }

    /** Подтверждение всех своих строк, которые ещё ждут ответа. */
    @Transactional
    public View confirm(String number, long sellerId) {
        PurchaseOrder o = owned(number, sellerId);
        if (o.getStatus() != OrderStatus.PAID) {
            throw BusinessException.unprocessable("not_paid_status", "Подтвердить можно только оплаченный заказ");
        }
        mine(o, sellerId).stream().filter(l -> l.getState() == LineState.PENDING).forEach(OrderLine::confirm);
        o.log("Продавец подтвердил заказ", sellerName(o, sellerId) + " отправит товар на хаб за " + props.shipDays() + " рабочих дня", "seller", Instant.now());
        settle(o);
        return view(o, sellerId);
    }

    /** Отказ по строкам (нет в наличии, брак). Деньги за них возвращаем покупателю сразу. */
    @Transactional
    public View decline(String number, long sellerId, List<Integer> lineNos, String reason) {
        PurchaseOrder o = owned(number, sellerId);
        if (o.getStatus() != OrderStatus.PAID && o.getStatus() != OrderStatus.CONFIRMED) {
            throw BusinessException.unprocessable("cannot_decline", "Отказаться можно до отправки на хаб");
        }
        String why = reason == null || reason.isBlank() ? "Нет в наличии" : reason.trim();
        List<Integer> targets = lineNos == null || lineNos.isEmpty()
                ? IntStream.range(0, o.getLines().size()).filter(i -> o.getLines().get(i).belongsTo(sellerId)).boxed().toList()
                : lineNos;
        for (int n : targets) {
            if (n < 0 || n >= o.getLines().size()) {
                throw BusinessException.unprocessable("bad_line", "Нет такой строки в заказе");
            }
            if (!o.getLines().get(n).belongsTo(sellerId)) {
                throw BusinessException.notFound("Строка заказа");
            }
        }
        closeLines(o, targets, why, "Продавец отказал: ", "seller");
        return view(o, sellerId);
    }

    /** Возврат денег за строки, которые продавец не отправит; если отправлять стало нечего — отмена заказа. */
    private void closeLines(PurchaseOrder o, List<Integer> targets, String why, String titlePrefix, String actor) {
        Instant now = Instant.now();
        long refund = 0;
        for (int n : targets) {
            OrderLine l = o.getLines().get(n);
            if (l.getState() == LineState.PENDING || l.getState() == LineState.CONFIRMED) {
                l.close(LineState.DECLINED, why);
                long rub = o.lineRub(l);
                refund += rub;
                o.log(titlePrefix + l.getTitle(), why + ". Вернём " + rub + " ₽", actor, now);
            }
        }
        o.refund(refund);
        if (!o.hasActiveLines() || o.onlyFoodLeft()) {
            o.cancel(o.hasActiveLines() ? "Остались только продукты, их не отправляем отдельно" : "Продавец не может выполнить заказ", actor, now);
        } else {
            settle(o);
        }
    }

    /** Каждые 15 минут: строки без ответа продавца дольше срока подтверждения — отменяем и возвращаем деньги. */
    @Scheduled(cron = "${pacco.orders.decline-cron:0 */15 * * * *}")
    @Transactional
    public int declineUnanswered() {
        Instant edge = Instant.now().minus(props.confirmHours(), ChronoUnit.HOURS);
        int done = 0;
        for (PurchaseOrder o : orders.findByStatusInOrderByIdAsc(List.of(OrderStatus.PAID))) {
            if (paidAt(o).isAfter(edge)) {
                continue;
            }
            List<Integer> pending = IntStream.range(0, o.getLines().size())
                    .filter(i -> o.getLines().get(i).getState() == LineState.PENDING).boxed().toList();
            if (!pending.isEmpty()) {
                closeLines(o, pending, "Продавец не подтвердил за " + props.confirmHours() + " часа", "Нет ответа продавца: ", "system");
                done++;
            }
        }
        return done;
    }

    /** Отправка своих подтверждённых товаров на хаб. */
    @Transactional
    public View ship(String number, long sellerId, String tracking) {
        PurchaseOrder o = owned(number, sellerId);
        if (o.getStatus() != OrderStatus.CONFIRMED) {
            throw BusinessException.unprocessable("not_confirmed", "Отправить на хаб можно после подтверждения всех продавцов заказа");
        }
        if (tracking == null || tracking.isBlank()) {
            throw BusinessException.unprocessable("tracking_required", "Укажите трек-номер отправки на хаб");
        }
        List<OrderLine> ready = mine(o, sellerId).stream().filter(l -> l.getState() == LineState.CONFIRMED).toList();
        if (ready.isEmpty()) {
            throw BusinessException.unprocessable("nothing_to_ship", "Все ваши товары по заказу уже отправлены");
        }
        String t = tracking.trim().toUpperCase();
        ready.forEach(l -> l.ship(t));
        Instant now = Instant.now();
        o.log("Продавец отправил товар на хаб", sellerName(o, sellerId) + ", трек " + t, "seller", now);
        if (o.getLines().stream().noneMatch(l -> l.getState() == LineState.CONFIRMED || l.getState() == LineState.PENDING)) {
            o.moveTo(OrderStatus.TO_HUB, "Едет на хаб в Милане", "Все продавцы отправили товары", "seller", now);
        }
        return view(o, sellerId);
    }

    /** Когда ни одна строка не ждёт ответа продавца — заказ подтверждён. */
    private void settle(PurchaseOrder o) {
        if (o.getStatus() == OrderStatus.PAID && o.getLines().stream().noneMatch(l -> l.getState() == LineState.PENDING)) {
            o.moveTo(OrderStatus.CONFIRMED, "Подтверждён продавцами", "Товары готовят к отправке на хаб", "seller", Instant.now());
        }
        if (o.getStatus() == OrderStatus.CONFIRMED
                && o.getLines().stream().noneMatch(l -> l.getState() == LineState.CONFIRMED || l.getState() == LineState.PENDING)) {
            o.moveTo(OrderStatus.TO_HUB, "Едет на хаб в Милане", "Все продавцы отправили товары", "seller", Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public Payouts payouts(long sellerId) {
        return registry().getOrDefault(sellerId, new Payouts(0, 0, 0, 0, 0, 0, 0));
    }

    /** Реестр выплат по всем продавцам: id продавца → суммы. Для финансов. */
    @Transactional(readOnly = true)
    public Map<Long, Payouts> registry() {
        Map<Long, long[]> acc = new TreeMap<>();
        for (PurchaseOrder o : orders.findWithSellerLines()) {
            boolean payable = PAYABLE.contains(o.getStatus());
            boolean pending = IN_PROGRESS.contains(o.getStatus());
            if (!payable && !pending) {
                continue;
            }
            for (OrderLine l : o.getLines()) {
                if (l.getSellerId() == null || !l.isActive()) {
                    continue;
                }
                long gross = l.getPriceEurCents() * l.getQty();
                long fee = BigDecimal.valueOf(gross).multiply(commission.rate(l.getCategory()))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                long[] a = acc.computeIfAbsent(l.getSellerId(), k -> new long[5]);
                if (payable) {
                    a[0] += gross;
                    a[1] += fee;
                    a[4]++;
                } else {
                    a[2] += gross;
                    a[3] += fee;
                }
            }
        }
        Map<Long, Payouts> out = new TreeMap<>();
        acc.forEach((id, a) -> out.put(id, new Payouts(a[0], a[1], a[0] - a[1], a[2], a[3], a[2] - a[3], a[4])));
        return out;
    }

    private PurchaseOrder owned(String number, long sellerId) {
        return orders.findByNumber(number)
                .filter(x -> x.getLines().stream().anyMatch(l -> l.belongsTo(sellerId)))
                .orElseThrow(() -> BusinessException.notFound("Заказ"));
    }

    private static List<OrderLine> mine(PurchaseOrder o, long sellerId) {
        return o.getLines().stream().filter(l -> l.belongsTo(sellerId)).toList();
    }

    private static String sellerName(PurchaseOrder o, long sellerId) {
        return mine(o, sellerId).stream().map(OrderLine::getSellerName).findFirst().orElse("Продавец");
    }

    private View view(PurchaseOrder o, long sellerId) {
        List<OrderLine> all = o.getLines();
        List<Line> lines = IntStream.range(0, all.size()).filter(i -> all.get(i).belongsTo(sellerId)).mapToObj(i -> {
            OrderLine l = all.get(i);
            return new Line(i, l.getProductId(), l.getTitle(), l.getSize(), l.getQty(), l.getPriceEurCents(),
                    l.isConfirmed(), l.getState(), l.getSellerTracking(), l.getNote());
        }).toList();
        boolean waiting = lines.stream().anyMatch(l -> l.state() == LineState.PENDING);
        boolean toShip = o.getStatus() == OrderStatus.CONFIRMED && lines.stream().anyMatch(l -> l.state() == LineState.CONFIRMED);
        return new View(o.getNumber(), o.getStatus(), o.getStatus().label(), o.getCreatedAt(), lines, !waiting,
                lines.stream().filter(l -> l.state().active()).mapToLong(l -> l.priceEurCents() * l.qty()).sum(),
                waiting && o.getStatus() == OrderStatus.PAID, toShip,
                paidAt(o).plus(props.confirmHours(), ChronoUnit.HOURS));
    }

    private static Instant paidAt(PurchaseOrder o) {
        return o.getEvents().stream().filter(e -> e.getStatus() == OrderStatus.PAID).map(e -> e.getAt()).findFirst()
                .orElse(o.getCreatedAt());
    }

    public record View(String number, OrderStatus status, String statusLabel, Instant createdAt, List<Line> lines,
                       boolean confirmedBySeller, long goodsEurCents, boolean needsAnswer, boolean needsShipping,
                       Instant confirmBy) {
    }

    public record Line(int lineNo, long productId, String title, String size, int qty, long priceEurCents,
                       boolean confirmed, LineState state, String tracking, String note) {
    }

    /** Суммы в евроцентах: к выплате по завершённым заказам и ожидается по заказам в пути. */
    public record Payouts(long payableGrossEurCents, long payableCommissionEurCents, long payableNetEurCents,
                          long pendingGrossEurCents, long pendingCommissionEurCents, long pendingNetEurCents,
                          long completedLines) {
    }
}
