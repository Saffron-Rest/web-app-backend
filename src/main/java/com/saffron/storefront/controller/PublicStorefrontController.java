package com.saffron.storefront.controller;

import com.saffron.storefront.service.PaymentService;
import com.saffron.storefront.service.SystemSettingsService;
import com.saffron.storefront.service.quotes.PickupVenue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight "give me the public-facing settings" endpoint the storefront
 * loads once on startup. Surfaces the operator-controlled banner + pause
 * toggles so the frontend can render a "We're temporarily closed" notice
 * without trying to checkout first.
 *
 * <p>Strictly read-only and contains no admin / payment / carrier secrets.
 */
@RestController
@RequestMapping("/api/storefront")
public class PublicStorefrontController {

    private final SystemSettingsService settings;
    private final PaymentService payments;
    private final PickupVenue pickup;

    public PublicStorefrontController(SystemSettingsService settings,
                                       PaymentService payments,
                                       PickupVenue pickup) {
        this.settings = settings;
        this.payments = payments;
        this.pickup = pickup;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("acceptingOrders",
                settings.getBoolean(SystemSettingsService.Key.ACCEPTING_ORDERS));
        out.put("acceptingReservations",
                settings.getBoolean(SystemSettingsService.Key.ACCEPTING_RESERVATIONS));
        String banner = settings.getString(SystemSettingsService.Key.BANNER_MESSAGE);
        Map<String, Object> bannerBlock = new LinkedHashMap<>();
        bannerBlock.put("message", banner == null ? "" : banner);
        bannerBlock.put("tone", settings.getString(SystemSettingsService.Key.BANNER_TONE));
        out.put("banner", bannerBlock);
        out.put("prepDelayMinutes",
                settings.getInt(SystemSettingsService.Key.PREP_DELAY_MINUTES, 0));
        Map<String, Object> venue = new LinkedHashMap<>();
        venue.put("name", pickup.name());
        venue.put("address", pickup.address());
        venue.put("phone", pickup.phone());
        venue.put("country", pickup.country());
        out.put("venue", venue);
        out.put("payment", Map.of("mock", payments.isMock()));
        return out;
    }
}
