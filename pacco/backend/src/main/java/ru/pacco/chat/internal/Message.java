package ru.pacco.chat.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "conversation_id", nullable = false)
    private long conversationId;
    /** BUYER, SELLER, ADMIN */
    @Column(name = "author_role", nullable = false)
    private String authorRole;
    @Column(name = "author_name", nullable = false)
    private String authorName;
    @Column(nullable = false)
    private String text;
    @Column(nullable = false)
    private boolean masked;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(long conversationId, String role, String name, String text, boolean masked) {
        this.conversationId = conversationId;
        this.authorRole = role;
        this.authorName = name;
        this.text = text;
        this.masked = masked;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public long getConversationId() { return conversationId; }
    public String getAuthorRole() { return authorRole; }
    public String getAuthorName() { return authorName; }
    public String getText() { return text; }
    public boolean isMasked() { return masked; }
    public Instant getCreatedAt() { return createdAt; }
}
