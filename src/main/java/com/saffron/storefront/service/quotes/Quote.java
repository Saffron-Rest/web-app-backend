package com.saffron.storefront.service.quotes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A delivery price quote from a single carrier.
 *
 * @param carrier         stable carrier identifier, e.g. {@code "wolt"}, {@code "glovo"},
 *                        {@code "dhl"}, {@code "dpd"}. The frontend uses this for branding.
 * @param mode            {@code INSTANT} (on-demand courier in Poland) or {@code COURIER}
 *                        (DHL/DPD style worldwide).
 * @param displayName     User-facing carrier name e.g. "Wolt Drive".
 * @param currency        ISO-4217 currency code (matches {@code app.currency}).
 * @param price           Total price the customer pays for delivery, incl. VAT.
 * @param etaMinutes      Best-effort ETA in minutes (instant) or {@code null} (courier).
 * @param transitDays     Estimated transit days (courier) or {@code null} (instant).
 * @param token           Carrier-specific quote token. Pass it back when booking the
 *                        delivery so the price is locked in.
 * @param expiresAt       When the quoted price stops being valid (typically 5–15 min).
 * @param live            True if this is a real upstream quote, false if it's the
 *                        deterministic mock fallback. The frontend can flag mocks if
 *                        you want to make this visible during onboarding.
 * @param notes           Optional human-readable note (e.g. "no couriers nearby").
 */
public record Quote(
        String carrier,
        DeliveryMode mode,
        String displayName,
        String currency,
        BigDecimal price,
        Integer etaMinutes,
        Integer transitDays,
        String token,
        Instant expiresAt,
        boolean live,
        String notes) {

    public boolean isAvailable() {
        return price != null && price.signum() >= 0;
    }
}
