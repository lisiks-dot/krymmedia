package ru.pacco.orders.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import ru.pacco.orders.LineState;
import ru.pacco.orders.OrderStatus;
import ru.pacco.orders.PaymentMethod;
import ru.pacco.pricing.DeliveryMode;

@Entity
@Table(name = "orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false)
    private DeliveryMode deliveryMode;

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    @Column(name = "buyer_phone", nullable = false)
    private String buyerPhone;

    @Column(name = "passport_masked", nullable = false)
    private String passportMasked;

    @Column(name = "pickup_point", nullable = false)
    private String pickupPoint;

    @Column(name = "goods_rub", nullable = false)
    private long goodsRub;

    @Column(name = "delivery_rub", nullable = false)
    private long deliveryRub;

    @Column(name = "duty_rub", nullable = false)
    private long dutyRub;

    @Column(name = "vat_rub", nullable = false)
    private long vatRub;

    @Column(name = "total_rub", nullable = false)
    private long totalRub;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "access_key", nullable = false)
    private UUID accessKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "refund_rub", nullable = false)
    private long refundRub;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "parcel_weight_g")
    private Integer parcelWeightG;

    @Column(name = "carrier_tracking")
    private String carrierTracking;

    @Column(name = "pickup_code")
    private String pickupCode;

    @Column(name = "storage_until")
    private LocalDate storageUntil;

    @Column(name = "received_at")
    private Instant receivedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<OrderLine> lines = new ArrayList<>();

    @OneToMany(cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("at asc, id asc")
    private List<OrderEvent> events = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String number, DeliveryMode deliveryMode, String buyerName, String buyerPhone,
                         String passportMasked, String pickupPoint, long goodsRub, long deliveryRub, long dutyRub,
                         long vatRub, long totalRub, List<OrderLine> lines) {
        this.number = number;
        this.status = OrderStatus.CREATED;
        this.deliveryMode = deliveryMode;
        this.buyerName = buyerName;
        this.buyerPhone = buyerPhone;
        this.passportMasked = passportMasked;
        this.pickupPoint = pickupPoint;
        this.goodsRub = goodsRub;
        this.deliveryRub = deliveryRub;
        this.dutyRub = dutyRub;
        this.vatRub = vatRub;
        this.totalRub = totalRub;
        this.lines = new ArrayList<>(lines);
        this.accessKey = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        log("Заказ оформлен", "Ждём оплату", "buyer", this.createdAt);
    }

    /** Смена статуса с записью в историю. */
    public void moveTo(OrderStatus next, Instant at) {
        moveTo(next, next.label(), null, "system", at);
    }

    public void moveTo(OrderStatus next, String title, String note, String actor, Instant at) {
        this.status = next;
        this.updatedAt = at;
        log(title, note, actor, at);
    }

    /** Событие без смены статуса: отказ по строке, замечание хаба, спор. */
    public void log(String title, String note, String actor, Instant at) {
        events.add(new OrderEvent(status, title, note, actor, at));
        this.updatedAt = at;
    }

    /** Доля строки в сумме товаров в рублях — по ней возвращаем деньги за отменённые позиции. */
    public long lineRub(OrderLine line) {
        long all = lines.stream().mapToLong(l -> l.getPriceEurCents() * l.getQty()).sum();
        if (all == 0) {
            return 0;
        }
        return BigDecimal.valueOf(goodsRub).multiply(BigDecimal.valueOf(line.getPriceEurCents() * line.getQty()))
                .divide(BigDecimal.valueOf(all), 0, RoundingMode.HALF_UP).longValue();
    }

    public OrderLine line(int lineNo) {
        if (lineNo < 0 || lineNo >= lines.size()) {
            throw new IllegalArgumentException("Нет строки " + lineNo);
        }
        return lines.get(lineNo);
    }

    public boolean hasActiveLines() {
        return lines.stream().anyMatch(OrderLine::isActive);
    }

    /** Остались только продукты — их не отправляем отдельно (правило площадки). */
    public boolean onlyFoodLeft() {
        return lines.stream().filter(OrderLine::isActive).allMatch(l -> l.getCategory() == ru.pacco.shared.Category.FOOD);
    }

    public void pay(PaymentMethod method, Instant at) {
        this.paymentMethod = method;
        moveTo(OrderStatus.PAID, "Оплачен", method == PaymentMethod.SBP ? "Через СБП" : "Картой МИР", "buyer", at);
    }

    public void refund(long rub) {
        this.refundRub = Math.min(totalRub, refundRub + rub);
    }

    /** Отмена с возвратом всего, что ещё не вернули. */
    public void cancel(String reason, String actor, Instant at) {
        boolean paid = status != OrderStatus.CREATED;
        lines.stream().filter(OrderLine::isActive).forEach(l -> l.close(LineState.DECLINED, reason));
        this.cancelReason = reason;
        if (paid) {
            this.refundRub = totalRub;
        }
        moveTo(OrderStatus.CANCELLED, "Заказ отменён", paid ? reason + ". Деньги вернутся на карту за 1–3 рабочих дня" : reason, actor, at);
    }

    public void packed(int weightG) { this.parcelWeightG = weightG; }
    public void handedOver(String tracking) { this.carrierTracking = tracking; }

    public void arrived(String code, LocalDate until) {
        this.pickupCode = code;
        this.storageUntil = until;
    }

    public void received(Instant at) { this.receivedAt = at; }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public OrderStatus getStatus() { return status; }
    public DeliveryMode getDeliveryMode() { return deliveryMode; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerPhone() { return buyerPhone; }
    public String getPassportMasked() { return passportMasked; }
    public String getPickupPoint() { return pickupPoint; }
    public long getGoodsRub() { return goodsRub; }
    public long getDeliveryRub() { return deliveryRub; }
    public long getDutyRub() { return dutyRub; }
    public long getVatRub() { return vatRub; }
    public long getTotalRub() { return totalRub; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderLine> getLines() { return lines; }
    public List<OrderEvent> getEvents() { return events; }
    public UUID getAccessKey() { return accessKey; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public long getRefundRub() { return refundRub; }
    public String getCancelReason() { return cancelReason; }
    public Integer getParcelWeightG() { return parcelWeightG; }
    public String getCarrierTracking() { return carrierTracking; }
    public String getPickupCode() { return pickupCode; }
    public LocalDate getStorageUntil() { return storageUntil; }
    public Instant getReceivedAt() { return receivedAt; }
}
