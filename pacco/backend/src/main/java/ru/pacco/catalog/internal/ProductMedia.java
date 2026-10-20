package ru.pacco.catalog.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Фото (IMAGE) или видео (VIDEO) товара. Сам файл — в {@link MediaStorage} по ключу. */
@Entity
@Table(name = "product_media")
public class ProductMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private long productId;
    @Column(nullable = false)
    private String kind;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;
    @Column(nullable = false)
    private int position;
    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductMedia() {
    }

    public ProductMedia(long productId, String kind, String contentType, long sizeBytes, String storageKey, int position, String uploadedBy) {
        this.productId = productId;
        this.kind = kind;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.position = position;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public void moveTo(int position) { this.position = position; }

    public Long getId() { return id; }
    public long getProductId() { return productId; }
    public String getKind() { return kind; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public int getPosition() { return position; }
}
