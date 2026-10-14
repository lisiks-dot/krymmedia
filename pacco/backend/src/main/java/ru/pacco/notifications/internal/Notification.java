package ru.pacco.notifications.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Отправленное (или неотправленное) сообщение покупателю — журнал для поддержки и ленты в кабинете. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "buyer_sub")
    private String buyerSub;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String recipient;
    private String subject;
    @Column(nullable = false)
    private String body;
    /** SENT — отправлено провайдером; LOGGED — провайдер не настроен, текст только в журнале; FAILED — ошибка. */
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String provider;
    private String error;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(Long orderId, String buyerSub, String channel, String recipient, String subject, String body,
                        String status, String provider, String error, Instant at) {
        this.orderId = orderId;
        this.buyerSub = buyerSub;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.provider = provider;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 480));
        this.createdAt = at;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
