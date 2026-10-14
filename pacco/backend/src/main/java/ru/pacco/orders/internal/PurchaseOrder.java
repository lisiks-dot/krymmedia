package ru.pacco.orders.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.Table;

import ru.pacco.orders.OrderStatus;
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLine> lines = new ArrayList<>();

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
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void moveTo(OrderStatus next, Instant at) {
        this.status = next;
        this.updatedAt = at;
    }

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
}
