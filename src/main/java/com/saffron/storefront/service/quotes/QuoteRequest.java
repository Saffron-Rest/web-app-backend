package com.saffron.storefront.service.quotes;

import java.math.BigDecimal;

/**
 * Inputs needed to price a delivery — same shape for instant and courier quotes.
 *
 * @param mode            {@link DeliveryMode#INSTANT} or {@link DeliveryMode#COURIER}.
 * @param dropoffLat,
 *         dropoffLng      Customer coordinates (decimal degrees). Optional for COURIER
 *                         when the postal address is enough.
 * @param dropoffAddress   Free-form address (line 1 + city + postal code).
 * @param dropoffCity      For DHL/DPD form fields.
 * @param dropoffPostal    For DHL/DPD form fields.
 * @param dropoffCountry   ISO-3166 alpha-2 country code, e.g. {@code "PL"}, {@code "DE"}.
 * @param contactName      Recipient name.
 * @param contactPhone     E.164 phone number (carriers require it).
 * @param totalWeightGrams Sum of item weights (parcel mass).
 * @param orderTotal       Total order value (used by some carriers as parcel "declared
 *                         value" — also lets us decide whether the order qualifies for
 *                         free delivery thresholds).
 * @param distanceKmHint   Optional pre-computed straight-line distance from the venue,
 *                         used by the mock fallback. {@code null} means "compute".
 */
public record QuoteRequest(
        DeliveryMode mode,
        Double dropoffLat,
        Double dropoffLng,
        String dropoffAddress,
        String dropoffCity,
        String dropoffPostal,
        String dropoffCountry,
        String contactName,
        String contactPhone,
        int totalWeightGrams,
        BigDecimal orderTotal,
        Double distanceKmHint) {
}
