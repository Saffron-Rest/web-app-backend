package com.saffron.storefront.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Shared {@link WebClient} for outbound carrier calls (Wolt, Glovo, DHL, DPD).
 * Single instance avoids the connection-pool warmup penalty per provider.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public WebClient carrierWebClient(WebClient.Builder builder) {
        return builder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .defaultHeader("User-Agent", "saffron-storefront/1.0")
                .build();
    }

    /** Conservative timeout for live carrier APIs. Used by reactive providers. */
    @Bean
    public Duration carrierRequestTimeout() {
        return Duration.ofSeconds(8);
    }
}
