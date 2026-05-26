package com.saffron.storefront.controller;

import com.saffron.storefront.service.quotes.DeliveryMode;
import com.saffron.storefront.service.quotes.QuoteRequest;
import com.saffron.storefront.service.quotes.QuoteService;
import com.saffron.storefront.web.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/quotes")
public class PublicQuoteController {

    private final QuoteService quoteService;

    public PublicQuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** Wolt vs Glovo (on-demand, Poland). */
    @PostMapping("/instant")
    public Map<String, Object> instant(@Valid @RequestBody QuoteBody body) {
        if (body.dropoffLat == null || body.dropoffLng == null) {
            throw new BadRequestException("Drop-off coordinates are required for instant delivery");
        }
        return quoteService.compare(DeliveryMode.INSTANT, body.toRequest(DeliveryMode.INSTANT));
    }

    /** DHL / DPD / etc. (parcel courier, worldwide). */
    @PostMapping("/courier")
    public Map<String, Object> courier(@Valid @RequestBody QuoteBody body) {
        if (body.dropoffCountry == null || body.dropoffCountry.isBlank()) {
            throw new BadRequestException("Country is required for courier shipping");
        }
        return quoteService.compare(DeliveryMode.COURIER, body.toRequest(DeliveryMode.COURIER));
    }

    public static class QuoteBody {
        public Double dropoffLat;
        public Double dropoffLng;
        @NotBlank public String dropoffAddress;
        public String dropoffCity;
        public String dropoffPostal;
        public String dropoffCountry;
        @NotBlank public String contactName;
        @NotBlank public String contactPhone;
        @Positive public int totalWeightGrams;
        public BigDecimal orderTotal;

        QuoteRequest toRequest(DeliveryMode mode) {
            return new QuoteRequest(
                    mode,
                    dropoffLat, dropoffLng,
                    dropoffAddress, dropoffCity, dropoffPostal, dropoffCountry,
                    contactName, contactPhone,
                    totalWeightGrams,
                    orderTotal,
                    null);
        }
    }
}
