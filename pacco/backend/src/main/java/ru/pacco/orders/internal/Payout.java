package ru.pacco.orders.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Выплата продавцу по реестру: READY — сформирована, ждёт перевода; PAID — деньги ушли, есть номер платёжки. */
@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String number;
    @Column(name = "seller_id", nullable = false)
    private long sellerId;
    @Column(name = "gross_eur_cents", nullable = false)
    private long grossEurCents;
    @Column(name = "commission_eur_cents", nullable = false)
    private long commissionEurCents;
    @Column(name = "net_eur_cents", nullable = false)
    private long netEurCents;
    @Column(name = "lines_count", nullable = false)
    private int linesCount;
    @Column(nullable = false)
    private String status;
    @Column(name = "bank_ref")
    private String bankRef;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payout() {
    }

    public Payout(String number, long sellerId, Instant at) {
        this.number = number;
        this.sellerId = sellerId;
        this.status = "READY";
        this.createdAt = at;
    }

    public void add(long gross, long commission) {
        grossEurCents += gross;
        commissionEurCents += commission;
        netEurCents = grossEurCents - commissionEurCents;
        linesCount++;
    }

    public void paid(String ref, Instant at) {
        this.status = "PAID";
        this.bankRef = ref;
        this.paidAt = at;
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public long getSellerId() { return sellerId; }
    public long getGrossEurCents() { return grossEurCents; }
    public long getCommissionEurCents() { return commissionEurCents; }
    public long getNetEurCents() { return netEurCents; }
    public int getLinesCount() { return linesCount; }
    public String getStatus() { return status; }
    public String getBankRef() { return bankRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
