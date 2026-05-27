package com.saffron.storefront.controller;

import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminOrderService;
import com.saffron.storefront.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) { this.service = service; }

    public record StatusRequest(String status, String message) {}
    public record CancelRequest(String reason) {}
    public record NoteRequest(String message) {}

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
}
