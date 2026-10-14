package ru.pacco.orders.internal;

import java.time.Instant;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Фото товара: с приёмки на хабе (HUB) или от покупателя к спору (DISPUTE). */
@Entity
@Table(name = "order_photos")
public class OrderPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "line_no")
    private Integer lineNo;

    @Column(nullable = false)
    private String kind;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderPhoto() {
    }

    public OrderPhoto(Long orderId, Integer lineNo, String kind, String contentType, byte[] data, String author, Instant at) {
        this.orderId = orderId;
        this.lineNo = lineNo;
        this.kind = kind;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.author = author;
        this.createdAt = at;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Integer getLineNo() { return lineNo; }
    public String getKind() { return kind; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }
}
