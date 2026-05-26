package com.saffron.storefront.service.quotes;

import reactor.core.publisher.Mono;

/**
 * One delivery carrier (Wolt Drive, Glovo Courier, DHL Express, DPD Polska, …).
 *
 * <p>Implementations are stateless and reactive so the {@link QuoteService} can fan out
 * to every provider in parallel within a few hundred milliseconds.
 *
 * <p>Each adapter must <b>never throw</b> — when the upstream is unreachable or returns
 * an error it should return a deterministic mock quote (or {@code Mono.empty()} if the
 * carrier cannot serve this geography at all). That way the customer always sees a
 * comparable price list even during partial outages.
 */
public interface QuoteProvider {

    /** Stable carrier identifier used in API responses, e.g. {@code "wolt"}. */
    String carrier();

    /** Display label for the storefront, e.g. {@code "Wolt Drive"}. */
    String displayName();

    /** Which kind of delivery this provider handles. */
    DeliveryMode mode();

    /** True when this provider has credentials and should be hit. */
    boolean isLiveEnabled();

    /** Returns a quote, or empty when the carrier cannot serve this destination. */
    Mono<Quote> quote(QuoteRequest req);
}
