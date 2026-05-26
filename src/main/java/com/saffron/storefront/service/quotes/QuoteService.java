package com.saffron.storefront.service.quotes;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fan-out coordinator — asks every registered provider for a quote in parallel and
 * returns the merged list, with the cheapest available option highlighted.
 *
 * <p>This is the heart of the "Wolt vs Glovo — show the cheapest" UX. Wolt and Glovo
 * adapters both respond in parallel, the price comparison happens server-side, and the
 * customer always sees both prices so they can choose (some customers prefer Wolt for
 * speed, some prefer Glovo for tip handling).
 */
@Service
public class QuoteService {

    private final List<QuoteProvider> providers;
    private final PickupVenue pickup;

    public QuoteService(List<QuoteProvider> providers, PickupVenue pickup) {
        this.providers = providers;
        this.pickup = pickup;
    }

    /** Comparison response for one delivery mode. Suitable for JSON serialisation. */
    public Map<String, Object> compare(DeliveryMode mode, QuoteRequest req) {
        Double distance = req.distanceKmHint();
        if (distance == null && req.dropoffLat() != null && req.dropoffLng() != null) {
            distance = Geo.haversineKm(pickup.lat(), pickup.lng(), req.dropoffLat(), req.dropoffLng());
        }
        final double distanceKm = distance == null ? -1 : distance;

        QuoteRequest enriched = new QuoteRequest(
                mode,
                req.dropoffLat(), req.dropoffLng(),
                req.dropoffAddress(), req.dropoffCity(), req.dropoffPostal(),
                req.dropoffCountry() == null ? pickup.country() : req.dropoffCountry(),
                req.contactName(), req.contactPhone(),
                Math.max(req.totalWeightGrams(), 100),
                req.orderTotal() == null ? BigDecimal.ZERO : req.orderTotal(),
                distance);

        List<Quote> quotes = Flux.fromIterable(providers)
                .filter(p -> p.mode() == mode)
                .flatMap(p -> p.quote(enriched)
                        .subscribeOn(Schedulers.parallel())
                        .onErrorResume(ex -> Mono.empty()))
                .filter(Objects::nonNull)
                .collectList()
                .block();

        if (quotes == null) quotes = List.of();

        Quote cheapest = quotes.stream()
                .filter(Quote::isAvailable)
                .min(Comparator.comparing(Quote::price))
                .orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode.name());
        if (distanceKm >= 0) body.put("distanceKm", round2(distanceKm));
        body.put("cheapestCarrier", cheapest != null ? cheapest.carrier() : null);
        body.put("currency", quotes.isEmpty() ? "PLN" : quotes.get(0).currency());
        body.put("quotes", quotes.stream().map(QuoteService::toMap).toList());
        return body;
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private static Map<String, Object> toMap(Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("carrier", q.carrier());
        m.put("displayName", q.displayName());
        m.put("mode", q.mode().name());
        m.put("currency", q.currency());
        m.put("price", q.price());
        m.put("available", q.isAvailable());
        if (q.etaMinutes() != null) m.put("etaMinutes", q.etaMinutes());
        if (q.transitDays() != null) m.put("transitDays", q.transitDays());
        m.put("token", q.token());
        m.put("expiresAt", q.expiresAt() == null ? null : q.expiresAt().toString());
        m.put("live", q.live());
        if (q.notes() != null) m.put("notes", q.notes());
        return m;
    }
}
