package com.datingapp.chat.block.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Block entity recording bi-directional blocking rules between users.
 */
@Entity
@Table(
        name = "blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_blocks_pair", columnNames = {"blocker_user_id", "blocked_user_id"}),
        indexes = @Index(name = "idx_blocks_blocked_user", columnList = "blocked_user_id")
)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_user_id", nullable = false)
    private Long blockerUserId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Block() {
    }

    public Block(Long blockerUserId, Long blockedUserId) {
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBlockerUserId() {
        return blockerUserId;
    }

    public void setBlockerUserId(Long blockerUserId) {
        this.blockerUserId = blockerUserId;
    }

    public Long getBlockedUserId() {
        return blockedUserId;
    }

    public void setBlockedUserId(Long blockedUserId) {
        this.blockedUserId = blockedUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
