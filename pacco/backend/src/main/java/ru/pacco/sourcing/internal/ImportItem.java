package ru.pacco.sourcing.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.pacco.sourcing.Scraped;

/** Товар поставщика в очереди модерации: NEW → APPROVED (создан товар каталога) или REJECTED. */
@Entity
@Table(name = "import_items")
public class ImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id")
    private Long jobId;
    @Column(nullable = false)
    private String source;
    @Column(name = "source_url", nullable = false, unique = true)
    private String sourceUrl;
    private String sku;
    private String brand;
    @Column(nullable = false)
    private String title;
    private String color;
    @Column(name = "category_hint")
    private String categoryHint;
    @Column(name = "price_eur_cents")
    private Long priceEurCents;
    private String currency;
    private String sizes;
    @Column(nullable = false)
    private boolean available;
    @Column(name = "image_url")
    private String imageUrl;
    @Column(nullable = false)
    private String status;
    @Column(name = "product_id")
    private Long productId;
    @Column(name = "markup_percent")
    private java.math.BigDecimal markupPercent;
    @Column(name = "reject_reason")
    private String rejectReason;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportItem() {
    }

    public ImportItem(Long jobId, String source, Scraped s) {
        this.jobId = jobId;
        this.source = source;
        this.sourceUrl = s.url();
        this.status = "NEW";
        apply(s);
    }

    /** Свежие данные поставщика; статус модерации не меняем. */
    public void apply(Scraped s) {
        this.sku = cut(s.sku(), 60);
        this.brand = cut(s.brand(), 200);
        this.title = cut(s.title(), 300);
        this.color = cut(s.color(), 100);
        this.categoryHint = cut(s.categoryHint(), 300);
        this.priceEurCents = s.priceEurCents();
        this.currency = s.currency();
        this.sizes = s.sizes().isEmpty() ? null : cut(String.join(",", s.sizes()), 500);
        this.available = s.available();
        this.imageUrl = cut(s.imageUrl(), 1000);
        this.fetchedAt = Instant.now();
        this.updatedAt = fetchedAt;
    }

    public void approve(long productId, java.math.BigDecimal markup) {
        this.status = "APPROVED";
        this.productId = productId;
        this.markupPercent = markup;
        this.rejectReason = null;
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        this.status = "REJECTED";
        this.rejectReason = cut(reason, 300);
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        this.status = "NEW";
        this.rejectReason = null;
        this.updatedAt = Instant.now();
    }

    public void attachTo(Long jobId) { this.jobId = jobId; }

    private static String cut(String s, int n) {
        return s == null ? null : s.length() <= n ? s : s.substring(0, n);
    }

    public List<String> sizeList() {
        return sizes == null ? List.of() : Arrays.stream(sizes.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSku() { return sku; }
    public String getBrand() { return brand; }
    public String getTitle() { return title; }
    public String getColor() { return color; }
    public String getCategoryHint() { return categoryHint; }
    public Long getPriceEurCents() { return priceEurCents; }
    public String getCurrency() { return currency; }
    public boolean isAvailable() { return available; }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public Long getProductId() { return productId; }
    public java.math.BigDecimal getMarkupPercent() { return markupPercent; }
    public String getRejectReason() { return rejectReason; }
    public Instant getFetchedAt() { return fetchedAt; }
}
