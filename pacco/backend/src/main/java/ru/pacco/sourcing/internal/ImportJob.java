package ru.pacco.sourcing.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Загрузка товаров: по адресу страницы Zalando (kind=PAGE) или из файла фида (kind=FEED). */
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String kind;
    private String url;
    @Column(name = "max_items", nullable = false)
    private int maxItems;
    /** RUNNING, DONE, FAILED */
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private int found;
    @Column(nullable = false)
    private int imported;
    @Column(nullable = false)
    private int failed;
    private String message;
    @Column(name = "started_by")
    private String startedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ImportJob() {
    }

    public ImportJob(String source, String kind, String url, int maxItems, String startedBy) {
        this.source = source;
        this.kind = kind;
        this.url = url;
        this.maxItems = maxItems;
        this.startedBy = startedBy;
        this.status = "RUNNING";
        this.createdAt = Instant.now();
    }

    public void found(int n) { this.found = n; }
    public void imported() { this.imported++; }
    public void failed(String why) { this.failed++; if (message == null) message = why; }
    public void finish(String status, String message) {
        this.status = status;
        if (message != null) this.message = message;
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getKind() { return kind; }
    public String getUrl() { return url; }
    public int getMaxItems() { return maxItems; }
    public String getStatus() { return status; }
    public int getFound() { return found; }
    public int getImported() { return imported; }
    public int getFailed() { return failed; }
    public String getMessage() { return message; }
    public String getStartedBy() { return startedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
