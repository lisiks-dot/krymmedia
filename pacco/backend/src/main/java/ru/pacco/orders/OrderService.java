package ru.pacco.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.buyers.BuyerProfiles;
import ru.pacco.cart.CartLine;
import ru.pacco.delivery.PickupPointView;
import ru.pacco.delivery.PickupPoints;
import ru.pacco.cart.CartService;
import ru.pacco.orders.OrderViews.Audience;
import ru.pacco.orders.internal.Dispute;
import ru.pacco.orders.internal.DisputeRepository;
import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PaymentGateway;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.pricing.DeliveryMode;
import ru.pacco.pricing.PricingService;
import ru.pacco.pricing.Quote;
import ru.pacco.pricing.QuoteLine;
import ru.pacco.shared.BusinessException;

/** Заказ глазами покупателя: оформление, оплата, отмена, спор. И ручное управление статусом для поддержки. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final DisputeRepository disputes;
    private final CartService carts;
    private final PricingService pricing;
    private final PaymentGateway payments;
    private final OrderViews views;
    private final PickupPoints points;
    private final BuyerProfiles profiles;

    public OrderService(OrderRepository orders, DisputeRepository disputes, CartService carts, PricingService pricing,
                        PaymentGateway payments, OrderViews views, PickupPoints points, BuyerProfiles profiles) {
        this.orders = orders;
        this.disputes = disputes;
        this.carts = carts;
        this.pricing = pricing;
        this.payments = payments;
        this.views = views;
        this.points = points;
        this.profiles = profiles;
    }

    /** Оформление; sub — аккаунт покупателя, если он вошёл (заказ будет виден на всех устройствах). */
    @Transactional
    public OrderView checkout(CheckoutRequest req, String sub) {
        List<String> errors = new ArrayList<>();
        if (digits(req.phone()).length() != 11) errors.add("телефон должен содержать 11 цифр");
        if (digits(req.passport()).length() != 10) errors.add("серия и номер паспорта — 10 цифр");
        if (digits(req.inn()).length() != 12) errors.add("ИНН физлица — 12 цифр");
        String pickupLabel = req.pickupPoint() == null ? "" : req.pickupPoint().trim();
        if (req.pickupPointId() != null) {
            pickupLabel = points.get(req.pickupPointId()).map(PickupPointView::label).orElse("");
            if (pickupLabel.isEmpty()) errors.add("пункт выдачи не найден, выберите другой");
        } else if (pickupLabel.isEmpty()) {
            errors.add("выберите пункт выдачи");
        }
        if (!errors.isEmpty()) {
            throw BusinessException.unprocessable("buyer_invalid", "Проверьте данные покупателя", errors);
        }

        List<CartLine> lines = carts.lines(req.cartId());
        if (lines.isEmpty()) {
            throw BusinessException.unprocessable("cart_empty", "Корзина пуста");
        }
        DeliveryMode mode = req.mode() == null ? DeliveryMode.GROUND : req.mode();
        Quote quote = pricing.quote(lines.stream().map(l -> new QuoteLine(l.product(), l.qty())).toList(), mode);
        if (quote.foodOnly()) {
            throw BusinessException.unprocessable("food_only",
                    "Гастрономию отправляем только вместе с товарами других категорий");
        }

        String number = "PC-" + orders.nextNumber();
        String passport = digits(req.passport());
        PurchaseOrder order = new PurchaseOrder(number, mode, req.buyerName().trim(), digits(req.phone()),
                "**** ***" + passport.substring(passport.length() - 3), pickupLabel,
                quote.goodsRub(), quote.deliveryRub(), quote.dutyRub(), quote.vatRub(), quote.totalRub(),
                lines.stream().map(l -> new OrderLine(l.product().id(), l.product().title(), l.product().sellerName(),
                        l.product().category(), l.size(), l.qty(), l.product().priceEurCents(), l.product().sellerId())).toList());
        String email = req.email() == null || req.email().isBlank() ? null : req.email().trim();
        order.contacts(email, req.pickupPointId());
        if (sub != null) {
            order.attachBuyer(sub);
            profiles.rememberCheckout(sub, req.buyerName().trim(), digits(req.phone()), email, req.pickupPointId());
        }
        orders.save(order);
        carts.clear(req.cartId());
        return views.view(order, Audience.BUYER);
    }

    /** Оплата. В MVP эквайринг — заглушка, которая сразу подтверждает платёж. */
    @Transactional
    public OrderView pay(String number, UUID key, String sub, PaymentMethod method) {
        PurchaseOrder order = requireOwned(number, key, sub);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw BusinessException.unprocessable("already_paid", "Заказ уже оплачен или отменён");
        }
        payments.charge(order.getNumber(), order.getTotalRub());
        order.pay(method == null ? PaymentMethod.MIR : method, Instant.now());
        return views.view(order, Audience.BUYER);
    }

    /** Покупатель отменяет заказ, пока продавцы не отправили товар на хаб. */
    @Transactional
    public OrderView cancel(String number, UUID key, String sub) {
        PurchaseOrder order = requireOwned(number, key, sub);
        if (!views.canCancel(order)) {
            throw BusinessException.unprocessable("cannot_cancel",
                    "Товар уже едет на хаб — отменить нельзя. После получения можно открыть спор");
        }
        order.cancel("Отменён покупателем", "buyer", Instant.now());
        return views.view(order, Audience.BUYER);
    }

    /** Спор по полученной посылке: в течение окна возврата, по выбранным строкам. */
    @Transactional
    public OrderView dispute(String number, UUID key, String sub, DisputeRequest req) {
        PurchaseOrder order = requireOwned(number, key, sub);
        Dispute last = disputes.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null);
        if (!views.canDispute(order, last)) {
            throw BusinessException.unprocessable("cannot_dispute",
                    last != null ? "По заказу уже открыт спор" : "Спор можно открыть в течение 14 дней после получения");
        }
        List<Integer> lines = new ArrayList<>(new HashSet<>(req.lines()));
        lines.sort(Integer::compare);
        for (int n : lines) {
            if (n < 0 || n >= order.getLines().size() || !order.line(n).isActive()) {
                throw BusinessException.unprocessable("bad_line", "Выберите товары из этого заказа");
            }
        }
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().trim();
        Instant now = Instant.now();
        disputes.save(new Dispute(order, lines, req.reason(), comment, now));
        order.logNotify("Открыт спор: " + req.reason().label().toLowerCase(), "Поддержка ответит в течение 2 рабочих дней", "buyer", now);
        return views.view(order, Audience.BUYER);
    }

    @Transactional(readOnly = true)
    public OrderView get(String number, UUID key, String sub) {
        PurchaseOrder order = require(number);
        return views.view(order, owns(order, key, sub) ? Audience.BUYER : Audience.PUBLIC);
    }

    /** Заказы аккаунта, новые сверху. */
    @Transactional(readOnly = true)
    public List<OrderView> mine(String sub) {
        return orders.findByBuyerSubOrderByIdDesc(sub).stream().map(o -> views.view(o, Audience.BUYER)).toList();
    }

    public record Claim(String number, UUID key) {
    }

    /** Привязать к аккаунту заказы, оформленные в этом браузере без входа (по ключу). Чужие и уже привязанные пропускаем. */
    @Transactional
    public List<OrderView> claim(String sub, List<Claim> claims) {
        for (Claim c : claims == null ? List.<Claim>of() : claims) {
            orders.findByNumber(c.number())
                    .filter(o -> o.getBuyerSub() == null && c.key() != null && c.key().equals(o.getAccessKey()))
                    .ifPresent(o -> {
                        o.attachBuyer(sub);
                        o.log("Заказ привязан к аккаунту", null, "buyer", Instant.now());
                    });
        }
        return mine(sub);
    }

    @Transactional(readOnly = true)
    public OrderView staffView(String number) {
        return views.view(require(number), Audience.STAFF);
    }

    @Transactional(readOnly = true)
    public List<OrderView> recent(int limit) {
        return orders.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id")))
                .map(o -> views.view(o, Audience.STAFF)).getContent();
    }

    /**
     * Ручной перевод на следующий статус — для поддержки, когда партнёр не прислал событие.
     * Обычный путь идёт через действия продавца, хаба, перевозчика и ПВЗ.
     */
    @Transactional
    public OrderView advance(String number) {
        PurchaseOrder order = require(number);
        if (order.getStatus().isFinal()) {
            throw BusinessException.unprocessable("final_status", "Заказ уже в конечном статусе");
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            throw BusinessException.unprocessable("not_paid", "Заказ ещё не оплачен");
        }
        OrderStatus next = order.getStatus().next();
        Instant now = Instant.now();
        if (next == OrderStatus.CONFIRMED) {
            order.getLines().stream().filter(l -> l.getState() == LineState.PENDING).forEach(OrderLine::confirm);
        }
        if (next == OrderStatus.RECEIVED) {
            order.received(now);
        }
        order.moveTo(next, next.label(), "Статус изменён вручную поддержкой", "support", now);
        return views.view(order, Audience.STAFF);
    }

    /** Отмена поддержкой — до передачи перевозчику. Деньги возвращаются полностью. */
    @Transactional
    public OrderView staffCancel(String number, String reason) {
        PurchaseOrder order = require(number);
        if (order.getStatus().isFinal() || order.getStatus().ordinal() >= OrderStatus.HANDED_TO_CARRIER.ordinal()) {
            throw BusinessException.unprocessable("cannot_cancel", "Посылка уже у перевозчика — отменить нельзя, только спор после получения");
        }
        String why = reason == null || reason.isBlank() ? "Отменён поддержкой" : reason.trim();
        order.cancel(why, "support", Instant.now());
        return views.view(order, Audience.STAFF);
    }

    PurchaseOrder require(String number) {
        return orders.findByNumber(number).orElseThrow(() -> BusinessException.notFound("Заказ"));
    }

    PurchaseOrder requireOwned(String number, UUID key, String sub) {
        PurchaseOrder order = require(number);
        if (!owns(order, key, sub)) {
            throw new BusinessException(403, "order_key", "Действие доступно только покупателю этого заказа", List.of());
        }
        return order;
    }

    /** Владелец — по ключу из браузера или СМС, либо по аккаунту. */
    private static boolean owns(PurchaseOrder o, UUID key, String sub) {
        return (key != null && key.equals(o.getAccessKey())) || (sub != null && sub.equals(o.getBuyerSub()));
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
