package com.saffron.storefront.controller;

import com.saffron.storefront.service.ReservationService;
import com.saffron.storefront.service.ReservationService.CreateReservationRequest;
import com.saffron.storefront.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final SystemSettingsService settings;

    public PublicReservationController(ReservationService service, SystemSettingsService settings) {
        this.service = service;
        this.settings = settings;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateReservationRequest req) {
        if (!settings.getBoolean(SystemSettingsService.Key.ACCEPTING_RESERVATIONS)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "We're not taking reservations at the moment. Please try again soon.");
        }
        return service.create(req);
    }
}
