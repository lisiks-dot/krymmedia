package ru.pacco.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.cart.CartLine;
import ru.pacco.cart.CartService;
import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PaymentGateway;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.pricing.DeliveryMode;
import ru.pacco.pricing.PricingService;
import ru.pacco.pricing.Quote;
import ru.pacco.pricing.QuoteLine;
import ru.pacco.shared.BusinessException;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CartService carts;
    private final PricingService pricing;
    private final PaymentGateway payments;

    public OrderService(OrderRepository orders, CartService carts, PricingService pricing, PaymentGateway payments) {
        this.orders = orders;
        this.carts = carts;
        this.pricing = pricing;
        this.payments = payments;
    }

    @Transactional
    public OrderView checkout(CheckoutRequest req) {
        List<String> errors = new ArrayList<>();
        if (digits(req.phone()).length() != 11) errors.add("телефон должен содержать 11 цифр");
        if (digits(req.passport()).length() != 10) errors.add("серия и номер паспорта — 10 цифр");
        if (digits(req.inn()).length() != 12) errors.add("ИНН физлица — 12 цифр");
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
                "**** ***" + passport.substring(passport.length() - 3), req.pickupPoint().trim(),
                quote.goodsRub(), quote.deliveryRub(), quote.dutyRub(), quote.vatRub(), quote.totalRub(),
                lines.stream().map(l -> new OrderLine(l.product().id(), l.product().title(), l.product().sellerName(),
                        l.product().category(), l.size(), l.qty(), l.product().priceEurCents())).toList());
        orders.save(order);
        carts.clear(req.cartId());
        return view(order);
    }

    /** Оплата. В MVP эквайринг — заглушка, которая сразу подтверждает платёж. */
    @Transactional
    public OrderView pay(String number) {
        PurchaseOrder order = require(number);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw BusinessException.unprocessable("already_paid", "Заказ уже оплачен");
        }
        payments.charge(order.getNumber(), order.getTotalRub());
        order.moveTo(OrderStatus.PAID, Instant.now());
        return view(order);
    }

    /** Следующий статус: в рабочей системе его ставят хаб, перевозчик и таможня. */
    @Transactional
    public OrderView advance(String number) {
        PurchaseOrder order = require(number);
        if (order.getStatus().isFinal()) {
            throw BusinessException.unprocessable("final_status", "Заказ уже в конечном статусе");
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            throw BusinessException.unprocessable("not_paid", "Заказ ещё не оплачен");
        }
        order.moveTo(order.getStatus().next(), Instant.now());
        return view(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(String number) {
        return view(require(number));
    }

    @Transactional(readOnly = true)
    public List<OrderView> recent(int limit) {
        return orders.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id")))
                .map(OrderService::view).getContent();
    }

    private PurchaseOrder require(String number) {
        return orders.findByNumber(number).orElseThrow(() -> BusinessException.notFound("Заказ"));
    }

    private static OrderView view(PurchaseOrder o) {
        return new OrderView(o.getNumber(), o.getStatus(), o.getStatus().label(), o.getStatus().ordinal() + 1,
                o.getDeliveryMode(), o.getBuyerName(), o.getPickupPoint(),
                o.getLines().stream().map(l -> new OrderView.Line(l.getProductId(), l.getTitle(), l.getSellerName(),
                        l.getCategory(), l.getSize(), l.getQty(), l.getPriceEurCents())).toList(),
                o.getGoodsRub(), o.getDeliveryRub(), o.getDutyRub(), o.getVatRub(), o.getTotalRub(),
                o.getCreatedAt(), o.getUpdatedAt());
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
