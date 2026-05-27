package com.saffron.storefront.controller;

import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminOrderService;
import com.saffron.storefront.service.AdminPaymentService;
import com.saffron.storefront.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;
    private final AdminPaymentService payments;

    public AdminOrderController(AdminOrderService service, AdminPaymentService payments) {
        this.service = service;
        this.payments = payments;
    }

    public record StatusRequest(String status, String message) {}
    public record CancelRequest(String reason) {}
    public record NoteRequest(String message) {}
    public record TrackingRequest(String trackingCode, String trackingUrl,
                                   String estimatedReadyAt, String estimatedDeliveryAt) {}
    public record MarkPaidRequest(String method, String note) {}
    public record RefundRequest(BigDecimal amount, String reason) {}

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        AuthHelper.currentUser();
        return service.list(status, q, page, size);
    }

    @GetMapping("/{reference}")
    public Map<String, Object> detail(@PathVariable String reference) {
        AuthHelper.currentUser();
        return service.detail(reference);
    }

    @PostMapping("/{reference}/status")
    public Map<String, Object> updateStatus(@PathVariable String reference,
                                            @RequestBody StatusRequest req,
                                            HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        if (req == null || req.status() == null) throw new BadRequestException("status required");
        OrderStatus next;
        try {
            next = OrderStatus.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + req.status());
        }
        return service.updateStatus(reference, next, req.message(), http);
    }

    @PostMapping("/{reference}/cancel")
    public Map<String, Object> cancel(@PathVariable String reference,
                                      @RequestBody(required = false) CancelRequest req,
                                      HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        return service.cancel(reference, req == null ? null : req.reason(), http);
    }

    @PostMapping("/{reference}/notes")
    public Map<String, Object> addNote(@PathVariable String reference,
                                       @RequestBody NoteRequest req,
                                       HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        return service.addEvent(reference, req == null ? null : req.message(), http);
    }

    /** Manually attach or correct courier tracking info for an order. */
    @PostMapping("/{reference}/tracking")
    public Map<String, Object> updateTracking(@PathVariable String reference,
                                              @RequestBody TrackingRequest req,
                                              HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        if (req == null) throw new BadRequestException("Empty request");
        return service.updateTracking(reference, req.trackingCode(), req.trackingUrl(),
                req.estimatedReadyAt(), req.estimatedDeliveryAt(), http);
    }

    /** Re-trigger delivery dispatch (fresh tracking code, fresh ETAs). */
    @PostMapping("/{reference}/redispatch")
    public Map<String, Object> redispatch(@PathVariable String reference, HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        return service.redispatch(reference, http);
    }

    /** Mark a PENDING_PAYMENT order as paid offline (bank transfer, cash, etc). */
    @PostMapping("/{reference}/mark-paid")
    public Map<String, Object> markPaid(@PathVariable String reference,
                                        @RequestBody(required = false) MarkPaidRequest req,
                                        HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        String method = req == null ? null : req.method();
        String note = req == null ? null : req.note();
        return payments.markPaid(reference, method, note, http);
    }

    /** Issue a refund — Stripe-backed if configured + paid via Stripe, otherwise manual. */
    @PostMapping("/{reference}/refund")
    public Map<String, Object> refund(@PathVariable String reference,
                                       @RequestBody(required = false) RefundRequest req,
                                       HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        BigDecimal amount = req == null ? null : req.amount();
        String reason = req == null ? null : req.reason();
        return payments.refund(reference, amount, reason, http);
    }
}
