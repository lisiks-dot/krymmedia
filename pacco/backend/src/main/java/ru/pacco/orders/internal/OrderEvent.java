package ru.pacco.orders.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.pacco.orders.OrderStatus;

/** Событие в истории заказа: из них строится трекинг покупателя и журнал для поддержки. */
@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private String title;

    private String note;

    /** buyer, seller, hub, carrier, customs, pickup, support, system. */
    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private Instant at;

    /** Нужно ли сообщить покупателю (исходящая очередь уведомлений). */
    @Column(nullable = false)
    private boolean notify;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    protected OrderEvent() {
    }

    OrderEvent(OrderStatus status, String title, String note, String actor, Instant at, boolean notify) {
        this.status = status;
        this.title = title;
        this.note = note;
        this.actor = actor;
        this.at = at;
        this.notify = notify;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void notified(Instant when) { this.notifiedAt = when; }

    public OrderStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public String getActor() { return actor; }
    public Instant getAt() { return at; }
}
