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
 * DHL Express worldwide courier.
 *
 * <p>API: https://developer.dhl.com/api-reference/dhl-express-mydhl-api ({@code POST /rates}).
 *
 * <p>Live mode kicks in when {@code app.dhl.express.enabled=true} and credentials are set.
 * For now the live path is a TODO — the mock returns a realistic-shape quote so the rest
 * of the storefront (checkout, comparison, order create) can be developed and tested.
 */
@Component
public class DhlExpressQuoteProvider implements QuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(DhlExpressQuoteProvider.class);

    private final boolean enabled;
    private final String currency;
    @SuppressWarnings("unused") private final String baseUrl;
    @SuppressWarnings("unused") private final String apiKey;
    @SuppressWarnings("unused") private final String apiSecret;
    @SuppressWarnings("unused") private final String account;
    @SuppressWarnings("unused") private final PickupVenue pickup;
    @SuppressWarnings("unused") private final WebClient http;
    @SuppressWarnings("unused") private final Duration timeout;

    public DhlExpressQuoteProvider(
            @Value("${app.dhl.express.enabled:false}") boolean enabled,
            @Value("${app.dhl.express.base-url}") String baseUrl,
            @Value("${app.dhl.express.api-key:}") String apiKey,
            @Value("${app.dhl.express.api-secret:}") String apiSecret,
            @Value("${app.dhl.express.account:}") String account,
            @Value("${app.currency:PLN}") String currency,
            PickupVenue pickup,
            WebClient carrierWebClient,
            Duration carrierRequestTimeout) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.account = account;
        this.currency = currency;
        this.pickup = pickup;
        this.http = carrierWebClient;
        this.timeout = carrierRequestTimeout;
    }

    @Override public String carrier() { return "dhl"; }
    @Override public String displayName() { return "DHL Express"; }
    @Override public DeliveryMode mode() { return DeliveryMode.COURIER; }
    @Override public boolean isLiveEnabled() {
        return enabled && !apiKey.isBlank() && !apiSecret.isBlank() && !account.isBlank();
    }

    @Override
    public Mono<Quote> quote(QuoteRequest req) {
        if (req.mode() != DeliveryMode.COURIER) return Mono.empty();
        if (isLiveEnabled()) {
            // TODO: implement live POST to {baseUrl}/rates with Basic auth (apiKey:apiSecret)
            // and the DHL Rate Request schema. Falls back to mock on any failure.
            log.debug("DHL live mode not yet wired — using mock quote.");
        }
        return Mono.just(MockQuote.dhl(req, currency));
    }
}
