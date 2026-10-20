package ru.pacco.chat.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Диалог покупателя с продавцом по товару. seller_id null — товар площадки, отвечает поддержка ilpacco. */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "buyer_sub", nullable = false)
    private String buyerSub;
    @Column(name = "buyer_name", nullable = false)
    private String buyerName;
    @Column(name = "seller_id")
    private Long sellerId;
    @Column(name = "seller_name", nullable = false)
    private String sellerName;
    @Column(name = "product_id")
    private Long productId;
    @Column(name = "product_title")
    private String productTitle;
    @Column(name = "order_number")
    private String orderNumber;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;
    @Column(name = "last_message")
    private String lastMessage;
    @Column(name = "buyer_unread", nullable = false)
    private int buyerUnread;
    @Column(name = "seller_unread", nullable = false)
    private int sellerUnread;
    @Column(name = "staff_unread", nullable = false)
    private int staffUnread;

    protected Conversation() {
    }

    public Conversation(String buyerSub, String buyerName, Long sellerId, String sellerName, Long productId, String productTitle, String orderNumber) {
        this.buyerSub = buyerSub;
        this.buyerName = buyerName;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.productId = productId;
        this.productTitle = productTitle;
        this.orderNumber = orderNumber;
        this.createdAt = Instant.now();
        this.lastMessageAt = createdAt;
    }

    /** Новое сообщение: счётчики непрочитанного у других участников. */
    public void posted(String role, String text, Instant at) {
        this.lastMessageAt = at;
        this.lastMessage = text.length() > 300 ? text.substring(0, 297) + "…" : text;
        switch (role) {
            case "BUYER" -> {
                if (sellerId != null) sellerUnread++;
                else staffUnread++;
            }
            case "SELLER" -> buyerUnread++;
            default -> {
                buyerUnread++;
                if (sellerId != null) sellerUnread++;
            }
        }
    }

    public void readBy(String role) {
        switch (role) {
            case "BUYER" -> buyerUnread = 0;
            case "SELLER" -> sellerUnread = 0;
            default -> staffUnread = 0;
        }
    }

    public void linkOrder(String number) {
        if (number != null) this.orderNumber = number;
    }

    public Long getId() { return id; }
    public String getBuyerSub() { return buyerSub; }
    public String getBuyerName() { return buyerName; }
    public Long getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public Long getProductId() { return productId; }
    public String getProductTitle() { return productTitle; }
    public String getOrderNumber() { return orderNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public String getLastMessage() { return lastMessage; }
    public int getBuyerUnread() { return buyerUnread; }
    public int getSellerUnread() { return sellerUnread; }
    public int getStaffUnread() { return staffUnread; }
}
