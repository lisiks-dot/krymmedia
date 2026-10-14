package ru.pacco.orders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import ru.pacco.orders.internal.Dispute;
import ru.pacco.orders.internal.DisputeRepository;
import ru.pacco.orders.internal.OrderPhotoRepository;
import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.PurchaseOrder;

/** Сборка {@link OrderView} с учётом того, кто смотрит. */
@Component
public class OrderViews {

    /** Покупатель с ключом, сотрудник или посторонний с одним номером заказа. */
    public enum Audience { BUYER, STAFF, PUBLIC }

    private static final List<OrderStatus> CANCELLABLE = List.of(OrderStatus.CREATED, OrderStatus.PAID, OrderStatus.CONFIRMED);

    private final DisputeRepository disputes;
    private final OrderPhotoRepository photos;
    private final OrdersProperties props;

    public OrderViews(DisputeRepository disputes, OrderPhotoRepository photos, OrdersProperties props) {
        this.disputes = disputes;
        this.photos = photos;
        this.props = props;
    }

    public Instant disputeUntil(PurchaseOrder o) {
        return o.getReceivedAt() == null ? null : o.getReceivedAt().plus(props.returnWindowDays(), ChronoUnit.DAYS);
    }

    public boolean canCancel(PurchaseOrder o) {
        return CANCELLABLE.contains(o.getStatus())
                && o.getLines().stream().noneMatch(l -> l.getState() == LineState.SHIPPED && l.getSellerId() != null);
    }

    public boolean canDispute(PurchaseOrder o, Dispute last) {
        Instant until = disputeUntil(o);
        return o.getStatus() == OrderStatus.RECEIVED && until != null && Instant.now().isBefore(until)
                && last == null && o.hasActiveLines();
    }

    public OrderView view(PurchaseOrder o, Audience who) {
        Dispute last = o.getId() == null ? null : disputes.findFirstByOrderIdOrderByIdDesc(o.getId()).orElse(null);
        boolean buyer = who == Audience.BUYER;
        boolean full = who != Audience.PUBLIC;
        List<OrderLine> lines = o.getLines();
        return new OrderView(o.getNumber(), o.getStatus(), o.getStatus().label(), o.getStatus().ordinal() + 1,
                o.getDeliveryMode(),
                full ? o.getBuyerName() : mask(o.getBuyerName()),
                who == Audience.STAFF ? maskPhone(o.getBuyerPhone()) : null,
                o.getPickupPoint(),
                java.util.stream.IntStream.range(0, lines.size()).mapToObj(i -> {
                    OrderLine l = lines.get(i);
                    return new OrderView.Line(i, l.getProductId(), l.getTitle(), l.getSellerName(), l.getSellerId(),
                            l.getCategory(), l.getSize(), l.getQty(), l.getPriceEurCents(), o.lineRub(l), l.getState(),
                            l.getState().label(), l.getSellerTracking(), l.getNote());
                }).toList(),
                o.getGoodsRub(), o.getDeliveryRub(), o.getDutyRub(), o.getVatRub(), o.getTotalRub(), o.getRefundRub(),
                o.getPaymentMethod(), o.getCancelReason(), o.getParcelWeightG(), o.getCarrierTracking(),
                buyer ? o.getPickupCode() : null, o.getStorageUntil(), o.getReceivedAt(), disputeUntil(o),
                buyer && o.getStatus() == OrderStatus.CREATED,
                buyer && canCancel(o),
                buyer && canDispute(o, last),
                buyer ? o.getAccessKey() : null,
                o.getEvents().stream().map(e -> new OrderView.Event(e.getStatus(), e.getTitle(), e.getNote(), e.getActor(), e.getAt())).toList(),
                last == null ? null : dispute(last),
                full && o.getId() != null ? photos.findByOrderIdOrderById(o.getId()).stream()
                        .map(m -> new OrderView.PhotoView(m.getId(), m.getLineNo(), m.getKind(), m.getCreatedAt())).toList() : List.of(),
                full ? o.getBuyerEmail() : null,
                o.getPickupPointId(),
                o.getBuyerSub() != null,
                o.getCreatedAt(), o.getUpdatedAt());
    }

    public static OrderView.DisputeView dispute(Dispute d) {
        return new OrderView.DisputeView(d.getId(), d.lines(), d.getReason(), d.getReason().label(), d.getComment(),
                d.getStatus(), d.getStatus().label(), d.getRefundRub(), d.getResolution(), d.getCreatedAt(), d.getResolvedAt());
    }

    static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            out.append(' ').append(parts[i].charAt(0)).append('.');
        }
        return out.toString();
    }

    static String maskPhone(String digits) {
        if (digits == null || digits.length() < 4) {
            return digits;
        }
        return "+7 *** ***-" + digits.substring(digits.length() - 4, digits.length() - 2) + "-" + digits.substring(digits.length() - 2);
    }
}
