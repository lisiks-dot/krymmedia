package ru.pacco.compliance.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "compliance_rules")
public class ComplianceRulesEntity {

    @Id
    private Integer id;

    @Column(name = "threshold_eur_cents", nullable = false)
    private long thresholdEurCents;

    @Column(name = "threshold_categories", nullable = false)
    private String thresholdCategories;

    @Column(name = "embargo_prefixes", nullable = false)
    private String embargoPrefixes;

    @Column(name = "alcohol_prefixes", nullable = false)
    private String alcoholPrefixes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ComplianceRulesEntity() {
    }

    public Integer getId() { return id; }
    public long getThresholdEurCents() { return thresholdEurCents; }
    public void setThresholdEurCents(long v) { this.thresholdEurCents = v; }
    public String getThresholdCategories() { return thresholdCategories; }
    public void setThresholdCategories(String v) { this.thresholdCategories = v; }
    public String getEmbargoPrefixes() { return embargoPrefixes; }
    public void setEmbargoPrefixes(String v) { this.embargoPrefixes = v; }
    public String getAlcoholPrefixes() { return alcoholPrefixes; }
    public void setAlcoholPrefixes(String v) { this.alcoholPrefixes = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }

    /** Для тестов. */
    public static ComplianceRulesEntity of(long thresholdEurCents, String categories, String embargo, String alcohol) {
        ComplianceRulesEntity e = new ComplianceRulesEntity();
        e.id = 1;
        e.thresholdEurCents = thresholdEurCents;
        e.thresholdCategories = categories;
        e.embargoPrefixes = embargo;
        e.alcoholPrefixes = alcohol;
        e.updatedAt = Instant.now();
        return e;
    }
}
