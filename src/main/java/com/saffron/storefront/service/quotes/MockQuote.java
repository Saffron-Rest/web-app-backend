package com.saffron.storefront.service.quotes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Deterministic mock quotes used when carrier credentials aren't configured (or the
 * upstream is unreachable). Designed to be <i>realistic enough</i> for end-to-end
 * testing — different carriers price the same trip slightly differently, so the
 * "cheaper one wins" UX behaves the same as in production.
 */
public final class MockQuote {

    private MockQuote() {}

    /** Wolt Drive — base 9 PLN + 2.20 PLN/km, capped at 15 km. */
    public static Quote wolt(QuoteRequest req, double distanceKm, String currency) {
        if (distanceKm > 15) {
            return unavailable("wolt", "Wolt Drive", DeliveryMode.INSTANT,
                    "Outside Wolt Drive coverage (mock)");
        }
        BigDecimal price = new BigDecimal("9.00")
                .add(BigDecimal.valueOf(2.20).multiply(BigDecimal.valueOf(distanceKm)))
                .setScale(2, RoundingMode.HALF_UP);
        int eta = 25 + (int) Math.round(distanceKm * 2.5);
        return mock("wolt", "Wolt Drive", DeliveryMode.INSTANT, currency, price, eta, null);
    }

    /** Glovo Express — base 10.50 PLN + 1.95 PLN/km, capped at 15 km. */
    public static Quote glovo(QuoteRequest req, double distanceKm, String currency) {
        if (distanceKm > 15) {
            return unavailable("glovo", "Glovo Courier", DeliveryMode.INSTANT,
                    "Outside Glovo Express coverage (mock)");
        }
        BigDecimal price = new BigDecimal("10.50")
                .add(BigDecimal.valueOf(1.95).multiply(BigDecimal.valueOf(distanceKm)))
                .setScale(2, RoundingMode.HALF_UP);
        int eta = 30 + (int) Math.round(distanceKm * 2.2);
        return mock("glovo", "Glovo Courier", DeliveryMode.INSTANT, currency, price, eta, null);
    }

    /** DHL Express — domestic 25 PLN, EU 80 PLN, world 180 PLN, +12 PLN/kg over 1 kg. */
    public static Quote dhl(QuoteRequest req, String currency) {
        BigDecimal base = baseCourierPrice(req.dropoffCountry(), new BigDecimal("25.00"),
                new BigDecimal("80.00"), new BigDecimal("180.00"));
        BigDecimal kgs = BigDecimal.valueOf(req.totalWeightGrams())
                .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
        BigDecimal extra = kgs.subtract(BigDecimal.ONE).max(BigDecimal.ZERO)
                .multiply(new BigDecimal("12.00"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(extra).setScale(2, RoundingMode.HALF_UP);
        int transit = transitDays(req.dropoffCountry(), 1, 2, 4);
        return mock("dhl", "DHL Express", DeliveryMode.COURIER, currency, total, null, transit);
    }

    /** DPD Polska — domestic 18 PLN, EU 65 PLN, world unavailable. */
    public static Quote dpd(QuoteRequest req, String currency) {
        String cc = req.dropoffCountry() == null ? "PL" : req.dropoffCountry().toUpperCase();
        if (!isPoland(cc) && !isEu(cc)) {
            return unavailable("dpd", "DPD Pickup", DeliveryMode.COURIER,
                    "DPD Polska doesn't ship outside the EU (mock)");
        }
        BigDecimal base = baseCourierPrice(cc, new BigDecimal("18.00"),
                new BigDecimal("65.00"), null);
        BigDecimal kgs = BigDecimal.valueOf(req.totalWeightGrams())
                .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
        BigDecimal extra = kgs.subtract(BigDecimal.ONE).max(BigDecimal.ZERO)
                .multiply(new BigDecimal("8.00"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(extra).setScale(2, RoundingMode.HALF_UP);
        int transit = isPoland(cc) ? 1 : 3;
        return mock("dpd", "DPD Pickup", DeliveryMode.COURIER, currency, total, null, transit);
    }

    private static BigDecimal baseCourierPrice(
            String country, BigDecimal domestic, BigDecimal eu, BigDecimal world) {
        String cc = country == null ? "PL" : country.toUpperCase();
        if (isPoland(cc)) return domestic;
        if (isEu(cc)) return eu;
        return world;
    }

    private static int transitDays(String country, int domestic, int eu, int world) {
        String cc = country == null ? "PL" : country.toUpperCase();
        if (isPoland(cc)) return domestic;
        if (isEu(cc)) return eu;
        return world;
    }

    private static boolean isPoland(String cc) { return "PL".equalsIgnoreCase(cc); }

    private static boolean isEu(String cc) {
        return java.util.Set.of(
                "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU",
                "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE"
        ).contains(cc);
    }

    public static Quote mock(
            String carrier, String displayName, DeliveryMode mode,
            String currency, BigDecimal price, Integer etaMinutes, Integer transitDays) {
        return new Quote(
                carrier,
                mode,
                displayName,
                currency,
                price,
                etaMinutes,
                transitDays,
                "mock-" + carrier + "-" + UUID.randomUUID(),
                Instant.now().plus(Duration.ofMinutes(10)),
                false,
                null);
    }

    public static Quote unavailable(String carrier, String displayName, DeliveryMode mode, String note) {
        return new Quote(
                carrier, mode, displayName, "PLN",
                null, null, null, null, null, false, note);
    }
}
