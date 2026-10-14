package ru.pacco.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import ru.pacco.orders.LineState;
import ru.pacco.shared.Category;

/** Строка заказа — снимок товара и цены на момент покупки. */
@Embeddable
public class OrderLine {

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private String title;

    @Column(name = "seller_name", nullable = false)
    private String sellerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private String size;

    @Column(nullable = false)
    private int qty;

    @Column(name = "price_eur_cents", nullable = false)
    private long priceEurCents;

    /** Продавец строки; null — товар оператора площадки, подтверждения не требует. */
    @Column(name = "seller_id")
    private Long sellerId;

    /** Устаревший флаг подтверждения; держим в согласии с {@link #state}. */
    @Column(nullable = false)
    private boolean confirmed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineState state = LineState.PENDING;

    @Column(name = "seller_tracking")
    private String sellerTracking;

    /** Причина отказа продавца или замечание хаба. */
    private String note;

    /** Выплата продавцу, в которую вошла строка; null — ещё не выплачена. */
    @Column(name = "payout_id")
    private Long payoutId;

    /** Комиссия, зафиксированная при формировании выплаты. */
    @Column(name = "commission_eur_cents")
    private Long commissionEurCents;

    protected OrderLine() {
    }

    public OrderLine(long productId, String title, String sellerName, Category category, String size, int qty,
                     long priceEurCents, Long sellerId) {
        this(productId, title, sellerName, category, size, qty, priceEurCents);
        this.sellerId = sellerId;
        // Товары оператора площадки уже лежат на хабе: подтверждать и отправлять их некому.
        if (sellerId == null) {
            moveTo(LineState.SHIPPED);
        }
    }

    public OrderLine(long productId, String title, String sellerName, Category category, String size, int qty,
                     long priceEurCents) {
        this.productId = productId;
        this.title = title;
        this.sellerName = sellerName;
        this.category = category;
        this.size = size;
        this.qty = qty;
        this.priceEurCents = priceEurCents;
    }

    public long getProductId() { return productId; }
    public String getTitle() { return title; }
    public String getSellerName() { return sellerName; }
    public Category getCategory() { return category; }
    public String getSize() { return size; }
    public int getQty() { return qty; }
    public long getPriceEurCents() { return priceEurCents; }
    public Long getSellerId() { return sellerId; }
    public LineState getState() { return state; }
    public String getSellerTracking() { return sellerTracking; }
    public String getNote() { return note; }
    public boolean isConfirmed() { return state != LineState.PENDING; }
    public boolean isActive() { return state.active(); }
    public boolean belongsTo(long seller) { return sellerId != null && sellerId == seller; }

    public void moveTo(LineState next) {
        this.state = next;
        this.confirmed = next != LineState.PENDING;
    }

    public void confirm() { moveTo(LineState.CONFIRMED); }

    public void ship(String tracking) {
        moveTo(LineState.SHIPPED);
        this.sellerTracking = tracking;
    }

    public void close(LineState reason, String note) {
        moveTo(reason);
        this.note = note;
    }

    public void remark(String note) { this.note = note; }
    public Long getPayoutId() { return payoutId; }
    public Long getCommissionEurCents() { return commissionEurCents; }

    public void includeIn(long payout, long commission) {
        this.payoutId = payout;
        this.commissionEurCents = commission;
    }
}
