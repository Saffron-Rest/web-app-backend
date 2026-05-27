package com.saffron.storefront.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * Append-only record of every state-changing admin action. Used to answer
 * "who changed this, when, and from where" — particularly useful for refunds,
 * product price changes, and reservation status overrides.
 */
@Entity
@Table(name = "storefront_audit_log", indexes = {
        @Index(name = "ix_audit_user", columnList = "user_id"),
        @Index(name = "ix_audit_entity", columnList = "entity,entity_id"),
        @Index(name = "ix_audit_created", columnList = "created_at")
})
public class StorefrontAuditLog {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "user_email", length = 200)
    private String userEmail;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 64)
    private String entity;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public Instant getCreatedAt() { return createdAt; }
}
