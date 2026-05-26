package com.saffron.storefront.controller;

import com.saffron.storefront.service.ReservationService;
import com.saffron.storefront.service.ReservationService.CreateReservationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public-facing table reservation endpoint used by the storefront's
 * Reservation modal. Returns the persisted reservation summary so the
 * UI can render a success state with the confirmed date/time/guest count
 * exactly as the backend stored it (avoids client/server drift on edge
 * cases like timezone or trimming).
 */
@RestController
@RequestMapping("/api/reservations")
public class PublicReservationController {

    private final ReservationService service;

    public PublicReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateReservationRequest req) {
        return service.create(req);
    }
}
