package com.saffron.storefront.service;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.repository.OrderEventRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminOrderService {

    private final CustomerOrderRepository orders;
    private final OrderEventRepository events;
    private final OrderService orderService;
    private final StorefrontAuditService audit;

    public AdminOrderService(CustomerOrderRepository orders,
                             OrderEventRepository events,
                             OrderService orderService,
                             StorefrontAuditService audit) {
        this.orders = orders;
        this.events = events;
        this.orderService = orderService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String statusStr, String query, int page, int size) {
        OrderStatus status = parseStatus(statusStr);
        Page<CustomerOrder> p = orders.searchOrders(
                status,
                (query == null || query.isBlank()) ? null : query.trim(),
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
