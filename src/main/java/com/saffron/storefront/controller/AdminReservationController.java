package com.saffron.storefront.controller;

import com.saffron.storefront.domain.Reservation;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminReservationService;
import com.saffron.storefront.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationController {

    private final AdminReservationService service;

    public AdminReservationController(AdminReservationService service) { this.service = service; }

    public record StatusRequest(String status) {}

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        AuthHelper.currentUser();
        return service.list(status, from, to, q, page, size);
    }

    @PostMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable String id,
                                            @RequestBody StatusRequest req,
                                            HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        if (req == null || req.status() == null) throw new BadRequestException("status required");
        Reservation.Status next;
        try {
            next = Reservation.Status.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + req.status());
        }
        return service.updateStatus(id, next, http);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, HttpServletRequest http) {
        AuthHelper.requireOrderWriter();
        service.delete(id, http);
        return Map.of("ok", true);
    }
}
