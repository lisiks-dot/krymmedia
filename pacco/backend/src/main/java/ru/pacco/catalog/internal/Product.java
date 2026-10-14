package ru.pacco.catalog.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.pacco.compliance.ItemSpec;
import ru.pacco.shared.Category;
import ru.pacco.shared.ProductSnapshot;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_name", nullable = false)
    private String sellerName;

    @Column(nullable = false)
    private String title;

    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "hs_code")
    private String hsCode;

    @Column(name = "price_eur_cents", nullable = false)
    private long priceEurCents;

    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;

    /** Размеры через запятую, итальянская сетка. */
    private String sizes;

    private String description;

    @Column(nullable = false)
    private boolean aerosol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String sellerName, String title, String region, Category category, String hsCode,
                   long priceEurCents, int weightGrams, List<String> sizes, String description, boolean aerosol) {
        this.sellerName = sellerName;
        this.title = title;
        this.region = region;
        this.category = category;
        this.hsCode = hsCode;
        this.priceEurCents = priceEurCents;
        this.weightGrams = weightGrams;
        this.sizes = sizes == null || sizes.isEmpty() ? null : String.join(",", sizes);
        this.description = description;
        this.aerosol = aerosol;
        this.status = ProductStatus.PUBLISHED;
        this.createdAt = Instant.now();
    }

    public ItemSpec toItemSpec() {
        return new ItemSpec(category, hsCode, priceEurCents, aerosol);
    }

    public ProductSnapshot toSnapshot() {
        return new ProductSnapshot(id, title, sellerName, category, hsCode, priceEurCents, weightGrams, sizeList());
    }

    public List<String> sizeList() {
        if (sizes == null || sizes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(sizes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void publish() {
        this.status = ProductStatus.PUBLISHED;
        this.blockReason = null;
    }

    public void block(String reason) {
        this.status = ProductStatus.BLOCKED;
        this.blockReason = reason;
    }

    public Long getId() { return id; }
    public String getSellerName() { return sellerName; }
    public String getTitle() { return title; }
    public String getRegion() { return region; }
    public Category getCategory() { return category; }
    public String getHsCode() { return hsCode; }
    public long getPriceEurCents() { return priceEurCents; }
    public int getWeightGrams() { return weightGrams; }
    public String getDescription() { return description; }
    public boolean isAerosol() { return aerosol; }
    public ProductStatus getStatus() { return status; }
    public String getBlockReason() { return blockReason; }
}
