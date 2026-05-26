package com.saffron.storefront.service.quotes.providers;

import com.saffron.storefront.service.quotes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Wolt Drive (Daas) on-demand courier in Poland.
 *
 * <p>API reference: https://developer.wolt.com/docs/api/daas/ — endpoint
 * {@code POST /v1/venues/{venue_id}/delivery-fee}.
 *
 * <p>This adapter sends a real quote request when {@code app.wolt.drive.enabled=true} and
 * the {@code merchant-id} + {@code api-key} are set; otherwise it falls back to a
 * deterministic distance-based mock.
 */
@Component
public class WoltDriveQuoteProvider implements QuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(WoltDriveQuoteProvider.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String merchantId;
    private final String apiKey;
    private final String currency;
    private final PickupVenue pickup;
    private final WebClient http;
    private final Duration timeout;

    public WoltDriveQuoteProvider(
            @Value("${app.wolt.drive.enabled:false}") boolean enabled,
            @Value("${app.wolt.drive.base-url}") String baseUrl,
            @Value("${app.wolt.drive.merchant-id:}") String merchantId,
            @Value("${app.wolt.drive.api-key:}") String apiKey,
            @Value("${app.currency:PLN}") String currency,
            PickupVenue pickup,
            WebClient carrierWebClient,
            Duration carrierRequestTimeout) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.merchantId = merchantId;
        this.apiKey = apiKey;
        this.currency = currency;
        this.pickup = pickup;
        this.http = carrierWebClient;
        this.timeout = carrierRequestTimeout;
    }

    @Override public String carrier() { return "wolt"; }
    @Override public String displayName() { return "Wolt Drive"; }
    @Override public DeliveryMode mode() { return DeliveryMode.INSTANT; }
    @Override public boolean isLiveEnabled() {
        return enabled && !merchantId.isBlank() && !apiKey.isBlank();
    }

    @Override
    public Mono<Quote> quote(QuoteRequest req) {
        if (req.mode() != DeliveryMode.INSTANT) return Mono.empty();
        double distance = distanceKm(req);
        if (!isLiveEnabled()) {
            return Mono.just(MockQuote.wolt(req, distance, currency));
        }
        return liveQuote(req, distance)
                .timeout(timeout)
                .onErrorResume(ex -> {
                    log.warn("Wolt Drive quote failed, using mock: {}", ex.getMessage());
                    return Mono.just(MockQuote.wolt(req, distance, currency));
                });
    }

    /** Real call to Wolt Daas — see Wolt docs for the exact payload. */
    private Mono<Quote> liveQuote(QuoteRequest req, double distance) {
        Map<String, Object> body = Map.of(
                "pickup", Map.of(
                        "location", Map.of("lat", pickup.lat(), "lon", pickup.lng()),
                        "comment", pickup.name()),
                "dropoff", Map.of(
                        "location", Map.of("lat", req.dropoffLat(), "lon", req.dropoffLng()),
                        "formatted_address", req.dropoffAddress(),
                        "name", req.contactName(),
                        "phone_number", req.contactPhone()),
                "min_preparation_time_minutes", 10);

        return http.post()
                .uri(baseUrl + "/v1/venues/" + merchantId + "/delivery-fee")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> parseLive(m, distance));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Quote parseLive(Map raw, double distance) {
        Map fee = (Map) raw.getOrDefault("price", Map.of());
        Object amount = fee.get("amount");
        String currency = (String) fee.getOrDefault("currency", this.currency);
        java.math.BigDecimal price = amount == null
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(amount.toString())
                        .movePointLeft(2)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
        Object eta = raw.getOrDefault("delivery_eta_minutes", null);
        Integer etaMinutes = eta == null ? 25 + (int) Math.round(distance * 2.5) : ((Number) eta).intValue();
        String token = String.valueOf(raw.getOrDefault("price_id", "wolt-live"));
        return new Quote(
                "wolt", DeliveryMode.INSTANT, displayName(),
                currency, price, etaMinutes, null, token,
                Instant.now().plus(Duration.ofMinutes(5)), true, null);
    }

    private double distanceKm(QuoteRequest req) {
        if (req.distanceKmHint() != null) return req.distanceKmHint();
        if (req.dropoffLat() == null || req.dropoffLng() == null) return 5.0;
        return Geo.haversineKm(pickup.lat(), pickup.lng(), req.dropoffLat(), req.dropoffLng());
    }
}
