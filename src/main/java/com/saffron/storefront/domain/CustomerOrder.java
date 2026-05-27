package com.saffron.storefront.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders", indexes = {
        @Index(name = "ix_orders_status", columnList = "status"),
        @Index(name = "ix_orders_created", columnList = "createdAt"),
        @Index(name = "ix_orders_email", columnList = "contactEmail"),
        @Index(name = "ix_orders_reference", columnList = "reference", unique = true)
})
public class CustomerOrder {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    /** Human-readable, short, customer-facing reference (e.g. "SF-7K3MQ2"). */
    @Column(nullable = false, length = 16)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_method", nullable = false, length = 32)
    private ShipmentMethod shipmentMethod;

    /** Carrier price token returned by /api/quotes, locked in at checkout time. */
    @Column(name = "quote_token", length = 200)
    private String quoteToken;

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 200)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 40)
    private String contactPhone;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_postal", length = 20)
    private String addressPostal;

    @Column(name = "address_country", length = 2)
    private String addressCountry;

    @Column(name = "address_lat")
    private Double addressLat;

    @Column(name = "address_lng")
    private Double addressLng;

    // --- VAT invoice (faktura VAT) request --------------------------------
    // Optional company billing details. Set when the customer opts in to a
    // VAT invoice at checkout (most B2B customers in PL need this).
    // Frontend resolves "same as delivery" to a concrete address before
    // posting, so the backend just stores whatever it receives.

    @Column(name = "wants_invoice", nullable = false)
    private boolean wantsInvoice = false;

    @Column(name = "invoice_company_name", length = 200)
    private String invoiceCompanyName;

    /** Polish NIP (10 digits, no separators) or future EU VAT id (e.g. "PL1234563218"). */
    @Column(name = "invoice_tax_id", length = 20)
    private String invoiceTaxId;

    @Column(name = "invoice_address_line", length = 300)
    private String invoiceAddressLine;

    @Column(name = "invoice_address_city", length = 100)
    private String invoiceAddressCity;

    @Column(name = "invoice_address_postal", length = 20)
    private String invoiceAddressPostal;

    @Column(name = "invoice_address_country", length = 2)
    private String invoiceAddressCountry;

    /** Customer-visible note attached to the order. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Sum of {@link OrderLine#getLineTotal()} — items, before delivery + tax. */
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "delivery_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryPrice = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "PLN";

    /** Stripe Checkout Session id once initiated. */
    @Column(name = "stripe_session_id", length = 200)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent", length = 200)
    private String stripePaymentIntent;

    /** Carrier shipment / tracking identifier once a delivery booking is confirmed. */
    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    /** Public tracking URL the customer can open to follow the courier. */
    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    /** Wall-clock ETA shown on the confirmation page (best-effort). */
    @Column(name = "estimated_ready_at")
    private Instant estimatedReadyAt;

    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    // --- Refund tracking ---------------------------------------------------
    // Populated by AdminPaymentService when a refund is issued (Stripe or
    // manual offline). Total may be partial, e.g. only the delivery fee.

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    /** Stripe `re_xxx` refund id, or a synthetic id like `manual-...` for offline. */
    @Column(name = "refund_reference", length = 200)
    private String refundReference;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderEvent> events = new ArrayList<>();

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public ShipmentMethod getShipmentMethod() { return shipmentMethod; }
    public void setShipmentMethod(ShipmentMethod m) { this.shipmentMethod = m; }
    public String getQuoteToken() { return quoteToken; }
    public void setQuoteToken(String t) { this.quoteToken = t; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String e) { this.contactEmail = e; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String p) { this.contactPhone = p; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String a) { this.addressLine = a; }
    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String c) { this.addressCity = c; }
    public String getAddressPostal() { return addressPostal; }
    public void setAddressPostal(String p) { this.addressPostal = p; }
    public String getAddressCountry() { return addressCountry; }
    public void setAddressCountry(String c) { this.addressCountry = c; }
    public Double getAddressLat() { return addressLat; }
    public void setAddressLat(Double v) { this.addressLat = v; }
    public Double getAddressLng() { return addressLng; }
    public void setAddressLng(Double v) { this.addressLng = v; }
    public boolean isWantsInvoice() { return wantsInvoice; }
    public void setWantsInvoice(boolean wantsInvoice) { this.wantsInvoice = wantsInvoice; }
    public String getInvoiceCompanyName() { return invoiceCompanyName; }
    public void setInvoiceCompanyName(String v) { this.invoiceCompanyName = v; }
    public String getInvoiceTaxId() { return invoiceTaxId; }
    public void setInvoiceTaxId(String v) { this.invoiceTaxId = v; }
    public String getInvoiceAddressLine() { return invoiceAddressLine; }
    public void setInvoiceAddressLine(String v) { this.invoiceAddressLine = v; }
    public String getInvoiceAddressCity() { return invoiceAddressCity; }
    public void setInvoiceAddressCity(String v) { this.invoiceAddressCity = v; }
    public String getInvoiceAddressPostal() { return invoiceAddressPostal; }
    public void setInvoiceAddressPostal(String v) { this.invoiceAddressPostal = v; }
    public String getInvoiceAddressCountry() { return invoiceAddressCountry; }
    public void setInvoiceAddressCountry(String v) { this.invoiceAddressCountry = v; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getDeliveryPrice() { return deliveryPrice; }
    public void setDeliveryPrice(BigDecimal v) { this.deliveryPrice = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String s) { this.stripeSessionId = s; }
    public String getStripePaymentIntent() { return stripePaymentIntent; }
    public void setStripePaymentIntent(String s) { this.stripePaymentIntent = s; }
    public List<OrderLine> getLines() { return lines; }
    public List<OrderEvent> getEvents() { return events; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public String getTrackingUrl() { return trackingUrl; }
    public void setTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
    public Instant getEstimatedReadyAt() { return estimatedReadyAt; }
    public void setEstimatedReadyAt(Instant estimatedReadyAt) { this.estimatedReadyAt = estimatedReadyAt; }
    public Instant getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
    public void setEstimatedDeliveryAt(Instant estimatedDeliveryAt) { this.estimatedDeliveryAt = estimatedDeliveryAt; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal v) { this.refundedAmount = v; }
    public String getRefundReference() { return refundReference; }
    public void setRefundReference(String v) { this.refundReference = v; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant v) { this.refundedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
