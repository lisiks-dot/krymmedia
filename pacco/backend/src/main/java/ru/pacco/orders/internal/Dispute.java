package ru.pacco.orders.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.pacco.orders.DisputeReason;
import ru.pacco.orders.DisputeStatus;

/** Спор покупателя по полученной посылке. Один открытый спор на заказ. */
@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;

    @Column(name = "line_nos", nullable = false)
    private String lineNos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeReason reason;

    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    @Column(name = "refund_rub", nullable = false)
    private long refundRub;

    private String resolution;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Dispute() {
    }

    public Dispute(PurchaseOrder order, List<Integer> lines, DisputeReason reason, String comment, Instant at) {
        this.order = order;
        this.lineNos = lines.stream().map(String::valueOf).collect(Collectors.joining(","));
        this.reason = reason;
        this.comment = comment;
        this.status = DisputeStatus.OPEN;
        this.createdAt = at;
    }

    public void resolve(DisputeStatus result, long refundRub, String resolution, Instant at) {
        this.status = result;
        this.refundRub = refundRub;
        this.resolution = resolution;
        this.resolvedAt = at;
    }

    public List<Integer> lines() {
        return Arrays.stream(lineNos.split(",")).filter(s -> !s.isBlank()).map(Integer::valueOf).toList();
    }

    public Long getId() { return id; }
    public PurchaseOrder getOrder() { return order; }
    public DisputeReason getReason() { return reason; }
    public String getComment() { return comment; }
    public DisputeStatus getStatus() { return status; }
    public long getRefundRub() { return refundRub; }
    public String getResolution() { return resolution; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
