package com.saffron.storefront.service;

import com.saffron.storefront.domain.*;
import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.repository.OrderEventRepository;
import com.saffron.storefront.repository.ProductRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final ProductRepository productRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final String defaultCurrency;

    public OrderService(
            ProductRepository productRepository,
            CustomerOrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            @Value("${app.currency:PLN}") String defaultCurrency) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public Map<String, Object> create(CreateOrderRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new BadRequestException("Order must contain at least one item");
        }
        ShipmentMethod method = parseMethod(req.shipmentMethod());

        CustomerOrder order = new CustomerOrder();
        order.setReference(nextReference());
        order.setShipmentMethod(method);
        order.setQuoteToken(req.quoteToken());
        order.setContactName(require(req.contactName(), "contactName"));
        order.setContactEmail(require(req.contactEmail(), "contactEmail"));
        order.setContactPhone(require(req.contactPhone(), "contactPhone"));
        order.setAddressLine(req.addressLine());
        order.setAddressCity(req.addressCity());
        order.setAddressPostal(req.addressPostal());
        order.setAddressCountry(req.addressCountry());
        order.setAddressLat(req.addressLat());
        order.setAddressLng(req.addressLng());

        // VAT invoice (faktura) opt-in. Lightweight server-side guards:
        // companyName + taxId are required when wantsInvoice is true; tax
        // id is stored stripped of separators so downstream invoicing
        // tools always see a canonical 10-digit (PL) NIP.
        if (Boolean.TRUE.equals(req.wantsInvoice())) {
            String companyName = trimToNull(req.invoiceCompanyName());
            String taxId = normalizeTaxId(req.invoiceTaxId());
            if (companyName == null) {
                throw new BadRequestException("invoiceCompanyName is required when wantsInvoice=true");
            }
            if (taxId == null) {
                throw new BadRequestException("invoiceTaxId is required when wantsInvoice=true");
            }
            order.setWantsInvoice(true);
            order.setInvoiceCompanyName(companyName);
            order.setInvoiceTaxId(taxId);
            order.setInvoiceAddressLine(trimToNull(req.invoiceAddressLine()));
            order.setInvoiceAddressCity(trimToNull(req.invoiceAddressCity()));
            order.setInvoiceAddressPostal(trimToNull(req.invoiceAddressPostal()));
            String country = trimToNull(req.invoiceAddressCountry());
            order.setInvoiceAddressCountry(country == null ? null : country.toUpperCase());
        } else {
            order.setWantsInvoice(false);
        }

        order.setNotes(req.notes());
        order.setCurrency(defaultCurrency);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (LinePayload lp : req.lines()) {
            if (lp.quantity() <= 0) throw new BadRequestException("Quantity must be positive");
            Product p = productRepository.findById(lp.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + lp.productId()));
            if (!p.isActive()) {
                throw new BadRequestException("Product is no longer available: " + p.getNamePl());
            }
            if (method == ShipmentMethod.INSTANT_WOLT || method == ShipmentMethod.INSTANT_GLOVO) {
                if (!p.isAvailableInstant()) {
                    throw new BadRequestException("'" + p.getNamePl()
                            + "' is not available for instant delivery");
                }
            }
            if (method == ShipmentMethod.COURIER_DHL || method == ShipmentMethod.COURIER_DPD) {
                if (!p.isAvailableCourier()) {
                    throw new BadRequestException("'" + p.getNamePl()
                            + "' is not available for courier shipping");
                }
            }

            OrderLine line = new OrderLine();
            line.setOrder(order);
            line.setProduct(p);
            line.setNameSnapshot(p.getNamePl());
            line.setQuantity(lp.quantity());
            line.setUnitPrice(p.getPrice());
            BigDecimal lineTotal = p.getPrice()
                    .multiply(BigDecimal.valueOf(lp.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            line.setLineTotal(lineTotal);
            order.getLines().add(line);
            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal deliveryPrice = req.deliveryPrice() == null
                ? BigDecimal.ZERO
                : req.deliveryPrice().setScale(2, RoundingMode.HALF_UP);
        order.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        order.setDeliveryPrice(deliveryPrice);
        order.setTotal(subtotal.add(deliveryPrice).setScale(2, RoundingMode.HALF_UP));

        order = orderRepository.save(order);
        appendEvent(order, OrderStatus.PENDING_PAYMENT, "Order placed");
        return toMap(order, orderEventRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getByReference(String reference) {
        CustomerOrder order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return toMap(order, orderEventRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    /**
     * Transition an order to {@code next} and append a timeline event. Returns the saved order
     * so callers (PaymentService, DeliveryDispatchService) can chain further work in one tx.
     */
    @Transactional
    public CustomerOrder transition(CustomerOrder order, OrderStatus next, String message) {
        if (order.getStatus() == next) return order;
        order.setStatus(next);
        order = orderRepository.save(order);
        appendEvent(order, next, message);
        return order;
    }

    @Transactional
    public CustomerOrder transition(String reference, OrderStatus next, String message) {
        CustomerOrder order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return transition(order, next, message);
    }

    /** Persist an audit-log entry. Tracking fields are optional (carriers attach them later). */
    @Transactional
    public void appendEvent(CustomerOrder order, OrderStatus status, String message) {
        appendEvent(order, status, message, null, null);
    }

    @Transactional
    public void appendEvent(CustomerOrder order, OrderStatus status, String message,
                            String trackingCode, String trackingUrl) {
        OrderEvent e = new OrderEvent(order, status, message);
        e.setTrackingCode(trackingCode);
        e.setTrackingUrl(trackingUrl);
        orderEventRepository.save(e);
    }

    private ShipmentMethod parseMethod(String s) {
        if (s == null || s.isBlank()) {
            throw new BadRequestException("shipmentMethod is required");
        }
        try {
            return ShipmentMethod.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown shipment method: " + s);
        }
    }

    private String nextReference() {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder sb = new StringBuilder("SF-");
            for (int i = 0; i < 6; i++) sb.append(REF_ALPHABET[RNG.nextInt(REF_ALPHABET.length)]);
            String ref = sb.toString();
            if (orderRepository.findByReference(ref).isEmpty()) return ref;
        }
        throw new IllegalStateException("Could not generate unique order reference");
    }

    private static String require(String v, String field) {
        if (v == null || v.isBlank()) throw new BadRequestException(field + " is required");
        return v;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /** Normalize a tax id: strip optional "PL" prefix, spaces, dashes. Returns null if empty. */
    private static String normalizeTaxId(String v) {
        if (v == null) return null;
        String t = v.replaceAll("[\\s-]", "").trim();
        if (t.toUpperCase().startsWith("PL")) {
            t = t.substring(2);
        }
        return t.isEmpty() ? null : t;
    }

    public static Map<String, Object> toMap(CustomerOrder o, List<OrderEvent> events) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("reference", o.getReference());
        m.put("status", o.getStatus().name());
        m.put("shipmentMethod", o.getShipmentMethod().name());
        m.put("currency", o.getCurrency());
        m.put("subtotal", o.getSubtotal());
        m.put("deliveryPrice", o.getDeliveryPrice());
        m.put("total", o.getTotal());
        m.put("contact", Map.of(
                "name", o.getContactName(),
                "email", o.getContactEmail(),
                "phone", o.getContactPhone()));
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("line", o.getAddressLine());
        address.put("city", o.getAddressCity());
        address.put("postal", o.getAddressPostal());
        address.put("country", o.getAddressCountry());
        m.put("address", address);

        // Optional VAT invoice block — always present in the response so
        // frontend can render a section ("Faktura VAT" pill); fields are
        // null when the customer didn't opt in.
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("wants", o.isWantsInvoice());
        invoice.put("companyName", o.getInvoiceCompanyName());
        invoice.put("taxId", o.getInvoiceTaxId());
        Map<String, Object> invoiceAddress = new LinkedHashMap<>();
        invoiceAddress.put("line", o.getInvoiceAddressLine());
        invoiceAddress.put("city", o.getInvoiceAddressCity());
        invoiceAddress.put("postal", o.getInvoiceAddressPostal());
        invoiceAddress.put("country", o.getInvoiceAddressCountry());
        invoice.put("address", invoiceAddress);
        m.put("invoice", invoice);

        m.put("notes", o.getNotes());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderLine l : o.getLines()) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("productId", l.getProduct().getId());
            lm.put("name", l.getNameSnapshot());
            lm.put("quantity", l.getQuantity());
            lm.put("unitPrice", l.getUnitPrice());
            lm.put("lineTotal", l.getLineTotal());
            lines.add(lm);
        }
        m.put("lines", lines);
        m.put("trackingCode", o.getTrackingCode());
        m.put("trackingUrl", o.getTrackingUrl());
        m.put("estimatedReadyAt", o.getEstimatedReadyAt() == null ? null : o.getEstimatedReadyAt().toString());
        m.put("estimatedDeliveryAt", o.getEstimatedDeliveryAt() == null ? null : o.getEstimatedDeliveryAt().toString());

        List<Map<String, Object>> evs = new ArrayList<>();
        if (events != null) {
            for (OrderEvent e : events) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("status", e.getStatus().name());
                em.put("message", e.getMessage());
                em.put("trackingCode", e.getTrackingCode());
                em.put("trackingUrl", e.getTrackingUrl());
                em.put("createdAt", e.getCreatedAt().toString());
                evs.add(em);
            }
        }
        m.put("events", evs);
        m.put("createdAt", o.getCreatedAt().toString());
        return m;
    }

    public record CreateOrderRequest(
            String shipmentMethod,
            String quoteToken,
            String contactName,
            String contactEmail,
            String contactPhone,
            String addressLine,
            String addressCity,
            String addressPostal,
            String addressCountry,
            Double addressLat,
            Double addressLng,
            Boolean wantsInvoice,
            String invoiceCompanyName,
            String invoiceTaxId,
            String invoiceAddressLine,
            String invoiceAddressCity,
            String invoiceAddressPostal,
            String invoiceAddressCountry,
            String notes,
            BigDecimal deliveryPrice,
            List<LinePayload> lines) {
    }

    public record LinePayload(String productId, int quantity) {}
}
