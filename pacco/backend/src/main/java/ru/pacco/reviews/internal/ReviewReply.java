package ru.pacco.reviews.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ответ на отзыв: продавец (SELLER) или ilpacco (ADMIN). */
@Entity
@Table(name = "review_replies")
public class ReviewReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "author_role", nullable = false)
    private String authorRole;
    @Column(name = "author_name", nullable = false)
    private String authorName;
    @Column(nullable = false)
    private String text;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewReply() {
    }

    ReviewReply(String role, String name, String text) {
        this.authorRole = role;
        this.authorName = name;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAuthorRole() { return authorRole; }
    public String getAuthorName() { return authorName; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
