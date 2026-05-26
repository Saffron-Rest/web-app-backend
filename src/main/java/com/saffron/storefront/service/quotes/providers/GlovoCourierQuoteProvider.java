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
import java.util.List;
import java.util.Map;

/**
 * Glovo Courier / Glovo Express on-demand courier.
 *
 * <p>API reference: https://api-docs.glovoapp.com/ ({@code POST /v1/laas/parcels/estimate}).
 *
 * <p>Live mode kicks in when {@code app.glovo.courier.enabled=true} and an API key is set.
 */
@Component
public class GlovoCourierQuoteProvider implements QuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(GlovoCourierQuoteProvider.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String currency;
    private final PickupVenue pickup;
    private final WebClient http;
    private final Duration timeout;

    public GlovoCourierQuoteProvider(
            @Value("${app.glovo.courier.enabled:false}") boolean enabled,
            @Value("${app.glovo.courier.base-url}") String baseUrl,
            @Value("${app.glovo.courier.api-key:}") String apiKey,
            @Value("${app.currency:PLN}") String currency,
            PickupVenue pickup,
            WebClient carrierWebClient,
            Duration carrierRequestTimeout) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.currency = currency;
        this.pickup = pickup;
        this.http = carrierWebClient;
        this.timeout = carrierRequestTimeout;
    }

    @Override public String carrier() { return "glovo"; }
    @Override public String displayName() { return "Glovo Courier"; }
    @Override public DeliveryMode mode() { return DeliveryMode.INSTANT; }
    @Override public boolean isLiveEnabled() { return enabled && !apiKey.isBlank(); }

    @Override
    public Mono<Quote> quote(QuoteRequest req) {
        if (req.mode() != DeliveryMode.INSTANT) return Mono.empty();
        double distance = distanceKm(req);
        if (!isLiveEnabled()) {
            return Mono.just(MockQuote.glovo(req, distance, currency));
        }
        return liveQuote(req)
                .timeout(timeout)
                .onErrorResume(ex -> {
                    log.warn("Glovo Courier quote failed, using mock: {}", ex.getMessage());
                    return Mono.just(MockQuote.glovo(req, distance, currency));
                });
    }

    private Mono<Quote> liveQuote(QuoteRequest req) {
        Map<String, Object> body = Map.of(
                "addresses", List.of(
                        Map.of(
                                "type", "PICK_UP",
                                "lat", pickup.lat(), "lon", pickup.lng(),
                                "label", pickup.name(),
                                "phone", pickup.phone()),
                        Map.of(
                                "type", "DELIVERY",
                                "lat", req.dropoffLat(), "lon", req.dropoffLng(),
                                "label", req.dropoffAddress(),
                                "phone", req.contactPhone(),
                                "contactPerson", req.contactName())),
                "packageDetails", Map.of(
                        "weight", Math.max(1, Math.round(req.totalWeightGrams() / 1000.0))));

        return http.post()
                .uri(baseUrl + "/v1/laas/parcels/estimate")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseLive);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Quote parseLive(Map raw) {
        Map cost = (Map) raw.getOrDefault("cost", Map.of());
        Object total = cost.get("total");
        String currency = (String) cost.getOrDefault("currency", this.currency);
        java.math.BigDecimal price = total == null
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(total.toString())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
        Object eta = raw.get("estimatedTimeOfArrival");
        Integer etaMinutes = eta == null ? 35 : ((Number) eta).intValue();
        String token = String.valueOf(raw.getOrDefault("id", "glovo-live"));
        return new Quote(
                "glovo", DeliveryMode.INSTANT, displayName(),
                currency, price, etaMinutes, null, token,
                Instant.now().plus(Duration.ofMinutes(10)), true, null);
    }

    private double distanceKm(QuoteRequest req) {
        if (req.distanceKmHint() != null) return req.distanceKmHint();
        if (req.dropoffLat() == null || req.dropoffLng() == null) return 5.0;
        return Geo.haversineKm(pickup.lat(), pickup.lng(), req.dropoffLat(), req.dropoffLng());
    }
}
