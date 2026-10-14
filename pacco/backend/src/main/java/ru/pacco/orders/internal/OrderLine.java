package ru.pacco.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

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

    protected OrderLine() {
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
}
