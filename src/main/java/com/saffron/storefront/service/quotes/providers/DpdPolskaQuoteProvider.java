package com.saffron.storefront.service.quotes.providers;

import com.saffron.storefront.service.quotes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * DPD Polska parcel courier (domestic PL + EU).
 *
 * <p>API: https://api.dpd.com.pl — SOAP/REST hybrid. Live wiring is a TODO; the mock
 * implements the same coverage rules (no shipping outside the EU).
 */
@Component
public class DpdPolskaQuoteProvider implements QuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(DpdPolskaQuoteProvider.class);

    private final boolean enabled;
    private final String currency;
    @SuppressWarnings("unused") private final String baseUrl;
    @SuppressWarnings("unused") private final String login;
    @SuppressWarnings("unused") private final String password;
    @SuppressWarnings("unused") private final String fid;
    @SuppressWarnings("unused") private final PickupVenue pickup;
    @SuppressWarnings("unused") private final WebClient http;
    @SuppressWarnings("unused") private final Duration timeout;

    public DpdPolskaQuoteProvider(
            @Value("${app.dpd.polska.enabled:false}") boolean enabled,
            @Value("${app.dpd.polska.base-url}") String baseUrl,
            @Value("${app.dpd.polska.login:}") String login,
            @Value("${app.dpd.polska.password:}") String password,
            @Value("${app.dpd.polska.fid:}") String fid,
            @Value("${app.currency:PLN}") String currency,
            PickupVenue pickup,
            WebClient carrierWebClient,
            Duration carrierRequestTimeout) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.login = login;
        this.password = password;
        this.fid = fid;
        this.currency = currency;
        this.pickup = pickup;
        this.http = carrierWebClient;
        this.timeout = carrierRequestTimeout;
    }

    @Override public String carrier() { return "dpd"; }
    @Override public String displayName() { return "DPD Pickup"; }
    @Override public DeliveryMode mode() { return DeliveryMode.COURIER; }
    @Override public boolean isLiveEnabled() {
        return enabled && !login.isBlank() && !password.isBlank() && !fid.isBlank();
    }

    @Override
    public Mono<Quote> quote(QuoteRequest req) {
        if (req.mode() != DeliveryMode.COURIER) return Mono.empty();
        if (isLiveEnabled()) {
            log.debug("DPD live mode not yet wired — using mock quote.");
            // TODO: SOAP call to {baseUrl} with WS-Security login/password.
        }
        return Mono.just(MockQuote.dpd(req, currency));
    }
}
