package com.saffron.storefront.service;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.domain.ShipmentMethod;
import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.repository.OrderEventRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminOrderService {

    private final CustomerOrderRepository orders;
    private final OrderEventRepository events;
    private final OrderService orderService;
    private final DeliveryDispatchService dispatchService;
    private final StorefrontAuditService audit;

    public AdminOrderService(CustomerOrderRepository orders,
                             OrderEventRepository events,
                             OrderService orderService,
                             DeliveryDispatchService dispatchService,
                             StorefrontAuditService audit) {
        this.orders = orders;
        this.events = events;
        this.orderService = orderService;
        this.dispatchService = dispatchService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String statusStr, String query, int page, int size) {
        OrderStatus status = parseStatus(statusStr);
        // Empty string = "no filter" — see CustomerOrderRepository for the rationale.
        Page<CustomerOrder> p = orders.searchOrders(
                status,
                (query == null || query.isBlank()) ? "" : query.trim(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
        List<Map<String, Object>> items = p.getContent().stream().map(AdminOrderService::summary).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p.getNumber());
        body.put("size", p.getSize());
        body.put("totalElements", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String reference) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    @Transactional
    public Map<String, Object> updateStatus(String reference, OrderStatus next, String message,
                                            HttpServletRequest http) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        orderService.transition(order, next,
                (message == null || message.isBlank()) ? "Status updated by admin" : message);
        audit.record("ORDER_STATUS", "CustomerOrder", order.getId(),
                "reference=" + reference + ",status=" + next.name(), http);
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    @Transactional
    public Map<String, Object> cancel(String reference, String reason, HttpServletRequest http) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Delivered orders cannot be cancelled — issue a refund instead");
        }
        orderService.transition(order, OrderStatus.CANCELLED,
                (reason == null || reason.isBlank()) ? "Cancelled by admin" : reason);
        audit.record("ORDER_CANCEL", "CustomerOrder", order.getId(),
                "reference=" + reference, http);
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    @Transactional
    public Map<String, Object> addEvent(String reference, String message, HttpServletRequest http) {
        if (message == null || message.isBlank()) throw new BadRequestException("Message required");
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        orderService.appendEvent(order, order.getStatus(), "Admin note: " + message.trim());
        audit.record("ORDER_NOTE", "CustomerOrder", order.getId(), message, http);
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    /** Manually attach / overwrite tracking + ETA fields. Useful when a carrier was booked
     * outside the storefront (phone call, retail dropoff) and the admin wants to surface
     * the courier info on the order confirmation page. */
    @Transactional
    public Map<String, Object> updateTracking(String reference, String trackingCode,
                                              String trackingUrl, String readyAtIso,
                                              String deliveryAtIso, HttpServletRequest http) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (trackingCode != null) order.setTrackingCode(blankToNull(trackingCode));
        if (trackingUrl != null)  order.setTrackingUrl(blankToNull(trackingUrl));
        if (readyAtIso != null)   order.setEstimatedReadyAt(parseInstant(readyAtIso));
        if (deliveryAtIso != null) order.setEstimatedDeliveryAt(parseInstant(deliveryAtIso));
        orders.save(order);
        orderService.appendEvent(order, order.getStatus(),
                "Tracking updated by admin",
                order.getTrackingCode(), order.getTrackingUrl());
        audit.record("ORDER_TRACKING", "CustomerOrder", order.getId(),
                "reference=" + reference + ",code=" + order.getTrackingCode(), http);
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    /** Re-runs {@link DeliveryDispatchService#handlePayment} to generate fresh tracking +
     * ETAs. Used when the original carrier failed and we need to rebook. */
    @Transactional
    public Map<String, Object> redispatch(String reference, HttpServletRequest http) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new BadRequestException("Cannot redispatch a " + order.getStatus().name() + " order");
        }
        if (order.getShipmentMethod() == ShipmentMethod.PICKUP) {
            throw new BadRequestException("Pickup orders have no carrier to redispatch");
        }
        dispatchService.handlePayment(order);
        audit.record("ORDER_REDISPATCH", "CustomerOrder", order.getId(),
                "reference=" + reference + ",method=" + order.getShipmentMethod(), http);
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid ISO-8601 instant: " + iso);
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static OrderStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OrderStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + s);
        }
    }

    public static Map<String, Object> summary(CustomerOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("reference", o.getReference());
        m.put("status", o.getStatus().name());
        m.put("shipmentMethod", o.getShipmentMethod() == null ? null : o.getShipmentMethod().name());
        m.put("total", o.getTotal());
        m.put("currency", o.getCurrency());
        m.put("contactName", o.getContactName());
        m.put("contactEmail", o.getContactEmail());
        m.put("contactPhone", o.getContactPhone());
        m.put("addressCity", o.getAddressCity());
        m.put("createdAt", o.getCreatedAt());
        m.put("updatedAt", o.getUpdatedAt());
        return m;
    }
}
