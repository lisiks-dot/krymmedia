package ru.pacco.sellers.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.pacco.sellers.SellerStatus;
import ru.pacco.sellers.SellerView;

@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_sub", nullable = false, unique = true)
    private String keycloakSub;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "partita_iva", nullable = false, unique = true)
    private String partitaIva;

    @Column(nullable = false)
    private String iban;

    private String region;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Seller() {
    }

    public Seller(String keycloakSub) {
        this.keycloakSub = keycloakSub;
        this.createdAt = Instant.now();
    }

    public void applyWith(String legalName, String partitaIva, String iban, String region, String contactEmail) {
        this.legalName = legalName;
        this.partitaIva = partitaIva;
        this.iban = iban;
        this.region = region;
        this.contactEmail = contactEmail;
        this.status = SellerStatus.PENDING;
        this.rejectReason = null;
    }

    public void approve() {
        this.status = SellerStatus.ACTIVE;
        this.rejectReason = null;
    }

    public void reject(String reason) {
        this.status = SellerStatus.REJECTED;
        this.rejectReason = reason;
    }

    public SellerView toView() {
        return new SellerView(id, legalName, partitaIva, iban, region, contactEmail, status, rejectReason, createdAt);
    }

    public String getKeycloakSub() { return keycloakSub; }
    public SellerStatus getStatus() { return status; }
}
