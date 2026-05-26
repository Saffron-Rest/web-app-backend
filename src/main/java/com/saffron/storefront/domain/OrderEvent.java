package com.saffron.storefront.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * Append-only audit log of every status transition / important event applied
 * to a {@link CustomerOrder}. Renders the timeline on the customer's
 * confirmation page and powers any future ops/admin "history" view.
 */
@Entity
@Table(name = "order_events", indexes = {
        @Index(name = "ix_order_events_order", columnList = "order_id"),
        @Index(name = "ix_order_events_created", columnList = "createdAt")
})
public class OrderEvent {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    /** Short, customer-safe message ("Płatność potwierdzona", etc.) — i18n on the FE. */
    @Column(length = 200)
    private String message;

    /** Optional carrier/tracking URL surfaced on the confirmation page. */
    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    /** Optional carrier tracking number / shipment id. */
    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public OrderEvent() {}

    public OrderEvent(CustomerOrder order, OrderStatus status, String message) {
        this.order = order;
        this.status = status;
        this.message = message;
    }

    public String getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public void setOrder(CustomerOrder order) { this.order = order; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTrackingUrl() { return trackingUrl; }
    public void setTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public Instant getCreatedAt() { return createdAt; }
}
