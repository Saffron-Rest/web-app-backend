package com.saffron.storefront.controller;

import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminPaymentService;
import com.saffron.storefront.service.PaymentService;
import com.saffron.storefront.service.SystemSettingsService;
import com.saffron.storefront.service.quotes.PickupVenue;
import com.saffron.storefront.service.quotes.QuoteProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only "system status" endpoint for the admin dashboard.
 *
 * <p>Surfaces which carrier adapters are live vs mocked, whether Stripe is
 * configured, the pickup venue currently in use (env-driven), and basic JVM /
 * DB liveness. Never returns secrets.
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private static final Logger log = LoggerFactory.getLogger(AdminSystemController.class);

    private final List<QuoteProvider> providers;
    private final PaymentService payments;
    private final AdminPaymentService adminPayments;
    private final SystemSettingsService settings;
    private final PickupVenue pickup;
    private final BuildProperties build;

    @Value("${app.timezone:Europe/Warsaw}")
    private String timezone;

    @Value("${app.currency:PLN}")
    private String currency;

    @Value("${app.delivery.progression.enabled:true}")
    private boolean progressionEnabled;

    @PersistenceContext
    private EntityManager em;

    public AdminSystemController(List<QuoteProvider> providers,
                                  PaymentService payments,
                                  AdminPaymentService adminPayments,
                                  SystemSettingsService settings,
                                  PickupVenue pickup,
                                  org.springframework.beans.factory.ObjectProvider<BuildProperties> buildProps) {
        this.providers = providers;
        this.payments = payments;
        this.adminPayments = adminPayments;
        this.settings = settings;
        this.pickup = pickup;
        // Build info is only present when spring-boot-maven-plugin writes META-INF/build-info.properties.
        // Fall back to null so the admin status page still renders in dev.
        this.build = buildProps.getIfAvailable();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        AuthHelper.currentUser();

        Map<String, Object> out = new LinkedHashMap<>();

        // ── Application / runtime metadata ──────────────────────────────────
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", build != null ? build.getName() : "saffron-storefront-api");
        app.put("version", build != null ? build.getVersion() : "dev");
        app.put("buildTime", build != null && build.getTime() != null ? build.getTime().toString() : null);
        app.put("timezone", timezone);
        app.put("currency", currency);
        app.put("uptimeSeconds", Duration.ofMillis(
                ManagementFactory.getRuntimeMXBean().getUptime()).getSeconds());
        app.put("startedAt", Instant.ofEpochMilli(
                ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
        out.put("app", app);

        // ── Delivery carriers ───────────────────────────────────────────────
        List<Map<String, Object>> carriers = new ArrayList<>();
        for (QuoteProvider p : providers) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("carrier", p.carrier());
            c.put("displayName", p.displayName());
            c.put("mode", p.mode().name());
            c.put("live", p.isLiveEnabled());
            carriers.add(c);
        }
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("carriers", carriers);
        delivery.put("demoProgressionEnabled", progressionEnabled);
        delivery.put("pickup", Map.of(
                "name", pickup.name(),
                "address", pickup.address(),
                "phone", pickup.phone(),
                "country", pickup.country()));
        out.put("delivery", delivery);

        // ── Payments ────────────────────────────────────────────────────────
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("provider", "stripe");
        payment.put("mode", payments.isMock() ? "mock" : "live");
        payment.put("refundsLive", adminPayments.stripeConfigured());
        out.put("payment", payment);

        // ── Storefront switches (DB-backed) ────────────────────────────────
        Map<String, Object> storefront = new LinkedHashMap<>();
        storefront.put("acceptingOrders", settings.getBoolean(SystemSettingsService.Key.ACCEPTING_ORDERS));
        storefront.put("acceptingReservations",
                settings.getBoolean(SystemSettingsService.Key.ACCEPTING_RESERVATIONS));
        storefront.put("bannerMessage", settings.getString(SystemSettingsService.Key.BANNER_MESSAGE));
        storefront.put("bannerTone", settings.getString(SystemSettingsService.Key.BANNER_TONE));
        out.put("storefront", storefront);

        // ── Database liveness ──────────────────────────────────────────────
        Map<String, Object> db = new LinkedHashMap<>();
        long t0 = System.nanoTime();
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            db.put("ok", true);
            db.put("latencyMs", (System.nanoTime() - t0) / 1_000_000.0);
        } catch (Exception ex) {
            log.warn("DB ping failed", ex);
            db.put("ok", false);
            db.put("error", ex.getMessage());
        }
        out.put("db", db);

        return out;
    }
}
