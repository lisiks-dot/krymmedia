package ru.pacco.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.shared.BusinessException;

/** Заказы с точки зрения продавца: только его строки, подтверждение отправки и расчёт выплат. */
@Service
public class SellerOrders {

    /** Заказы, по которым продавец уже получил деньги или получит в ближайшем реестре. */
    private static final Set<OrderStatus> PAYABLE = Set.of(OrderStatus.COMPLETED);
    private static final Set<OrderStatus> IN_PROGRESS = Set.of(OrderStatus.PAID, OrderStatus.CONFIRMED,
            OrderStatus.TO_HUB, OrderStatus.AT_HUB, OrderStatus.CONSOLIDATED, OrderStatus.HANDED_TO_CARRIER,
            OrderStatus.IN_CUSTOMS, OrderStatus.RELEASED, OrderStatus.AT_PICKUP_POINT, OrderStatus.RECEIVED);

    private final OrderRepository orders;
    private final CommissionProperties commission;

    public SellerOrders(OrderRepository orders, CommissionProperties commission) {
        this.orders = orders;
        this.commission = commission;
    }

    @Transactional(readOnly = true)
    public List<View> list(long sellerId) {
        return orders.findBySeller(sellerId).stream()
                .filter(o -> o.getStatus() != OrderStatus.CREATED)
                .map(o -> view(o, sellerId))
                .toList();
    }

    /**
     * Продавец подтверждает, что отправит свои товары на хаб. Когда подтверждены все строки заказа,
     * заказ переходит в статус «Подтверждён продавцом».
     */
    @Transactional
    public View confirm(String number, long sellerId) {
        PurchaseOrder o = orders.findByNumber(number)
                .filter(x -> x.getLines().stream().anyMatch(l -> Long.valueOf(sellerId).equals(l.getSellerId())))
                .orElseThrow(() -> BusinessException.notFound("Заказ"));
        if (o.getStatus() != OrderStatus.PAID) {
            throw BusinessException.unprocessable("not_paid_status", "Подтвердить можно только оплаченный заказ");
        }
        o.getLines().stream().filter(l -> Long.valueOf(sellerId).equals(l.getSellerId())).forEach(OrderLine::confirm);
        if (o.getLines().stream().allMatch(OrderLine::isConfirmed)) {
            o.moveTo(OrderStatus.CONFIRMED, Instant.now());
        }
        return view(o, sellerId);
    }

    @Transactional(readOnly = true)
    public Payouts payouts(long sellerId) {
        long payableGross = 0, payableCommission = 0, pendingGross = 0, pendingCommission = 0;
        for (PurchaseOrder o : orders.findBySeller(sellerId)) {
            boolean payable = PAYABLE.contains(o.getStatus());
            boolean pending = IN_PROGRESS.contains(o.getStatus());
            if (!payable && !pending) {
                continue;
            }
            for (OrderLine l : o.getLines()) {
                if (!Long.valueOf(sellerId).equals(l.getSellerId())) {
                    continue;
                }
                long gross = l.getPriceEurCents() * l.getQty();
                long fee = BigDecimal.valueOf(gross).multiply(commission.rate(l.getCategory()))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                if (payable) {
                    payableGross += gross;
                    payableCommission += fee;
                } else {
                    pendingGross += gross;
                    pendingCommission += fee;
                }
            }
        }
        return new Payouts(payableGross, payableCommission, payableGross - payableCommission,
                pendingGross, pendingCommission, pendingGross - pendingCommission);
    }

    private static View view(PurchaseOrder o, long sellerId) {
        List<OrderLine> mine = o.getLines().stream().filter(l -> Long.valueOf(sellerId).equals(l.getSellerId())).toList();
        return new View(o.getNumber(), o.getStatus(), o.getStatus().label(), o.getCreatedAt(),
                mine.stream().map(l -> new Line(l.getProductId(), l.getTitle(), l.getSize(), l.getQty(),
                        l.getPriceEurCents(), l.isConfirmed())).toList(),
                mine.stream().allMatch(OrderLine::isConfirmed),
                mine.stream().mapToLong(l -> l.getPriceEurCents() * l.getQty()).sum());
    }

    public record View(String number, OrderStatus status, String statusLabel, Instant createdAt, List<Line> lines,
                       boolean confirmedBySeller, long goodsEurCents) {
    }

    public record Line(long productId, String title, String size, int qty, long priceEurCents, boolean confirmed) {
    }

    /** Суммы в евроцентах: к выплате по завершённым заказам и ожидается по заказам в пути. */
    public record Payouts(long payableGrossEurCents, long payableCommissionEurCents, long payableNetEurCents,
                          long pendingGrossEurCents, long pendingCommissionEurCents, long pendingNetEurCents) {
    }
}
