package ru.pacco.reviews.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private long productId;
    @Column(name = "buyer_sub", nullable = false)
    private String buyerSub;
    @Column(name = "author_name", nullable = false)
    private String authorName;
    @Column(name = "order_number", nullable = false)
    private String orderNumber;
    private String size;
    @Column(nullable = false)
    private short rating;
    @Column(nullable = false)
    private String text;
    @Column(nullable = false)
    private boolean hidden;
    @Column(name = "hidden_reason")
    private String hiddenReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "review_id", nullable = false)
    @OrderBy("id asc")
    private List<ReviewReply> replies = new ArrayList<>();

    protected Review() {
    }

    public Review(long productId, String buyerSub, String authorName, String orderNumber, String size, int rating, String text) {
        this.productId = productId;
        this.buyerSub = buyerSub;
        this.authorName = authorName;
        this.orderNumber = orderNumber;
        this.size = size;
        this.rating = (short) rating;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public void reply(String role, String name, String text) {
        replies.add(new ReviewReply(role, name, text));
    }

    public void hide(String reason) {
        this.hidden = true;
        this.hiddenReason = reason;
    }

    public void show() {
        this.hidden = false;
        this.hiddenReason = null;
    }

    public Long getId() { return id; }
    public long getProductId() { return productId; }
    public String getBuyerSub() { return buyerSub; }
    public String getAuthorName() { return authorName; }
    public String getOrderNumber() { return orderNumber; }
    public String getSize() { return size; }
    public int getRating() { return rating; }
    public String getText() { return text; }
    public boolean isHidden() { return hidden; }
    public String getHiddenReason() { return hiddenReason; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ReviewReply> getReplies() { return replies; }
}
