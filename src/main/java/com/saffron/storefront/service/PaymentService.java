package com.saffron.storefront.service;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderLine;
import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges {@link CustomerOrder} to Stripe Checkout.
 *
 * <ul>
 *   <li><b>Real mode</b> – when {@code app.stripe.secret-key} is non-blank we POST to
 *       {@code /v1/checkout/sessions} and persist the returned session id on the order.</li>
 *   <li><b>Mock mode</b> – otherwise we return a {@code /order/{ref}/mock-pay} URL on the
 *       storefront frontend. The mock-pay page later calls {@code mockComplete()} to flip
 *       the order to {@link OrderStatus#PAID}. This lets the entire purchase flow work
 *       end-to-end locally with zero Stripe configuration.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String STRIPE_API = "https://api.stripe.com";

    private final CustomerOrderRepository orderRepository;
    private final OrderService orderService;
    private final DeliveryDispatchService dispatchService;
    private final String secretKey;
    private final String successUrlTemplate;
    private final String cancelUrl;
    private final String publicBaseUrl;
    private final RestClient stripeRest;

    public PaymentService(
            CustomerOrderRepository orderRepository,
            OrderService orderService,
            DeliveryDispatchService dispatchService,
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.success-url:http://localhost:5174/order/{REF}?paid=1}") String successUrlTemplate,
            @Value("${app.stripe.cancel-url:http://localhost:5174/checkout?canceled=1}") String cancelUrl,
            @Value("${app.stripe.public-base-url:http://localhost:5174}") String publicBaseUrl) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.dispatchService = dispatchService;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.successUrlTemplate = successUrlTemplate;
        this.cancelUrl = cancelUrl;
        this.publicBaseUrl = trimTrailing(publicBaseUrl);
        this.stripeRest = RestClient.builder().baseUrl(STRIPE_API).build();
    }

    public boolean isMock() {
        return secretKey.isBlank();
    }

    /**
     * Create (or look up) the payment session for an order and return the URL the customer
     * should be redirected to. Idempotent: calling twice on the same order reuses the existing
     * session id when present.
     */
    @Transactional
    public Map<String, Object> initiateCheckout(String reference) {
        CustomerOrder order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            // Already paid (or beyond) — just send them to the confirmation page.
            return Map.of(
                    "url", buildSuccessUrl(reference),
                    "mock", isMock(),
                    "alreadyPaid", true,
                    "reference", reference);
        }

        if (isMock()) {
            String url = publicBaseUrl + "/order/" + reference + "/mock-pay";
            return Map.of(
                    "url", url,
                    "mock", true,
                    "reference", reference);
        }

        // ---------- Real Stripe Checkout Session ----------
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("client_reference_id", order.getReference());
        form.add("customer_email", order.getContactEmail());
        form.add("success_url", buildSuccessUrl(reference));
        form.add("cancel_url", cancelUrl);
        // Poland-relevant methods. Stripe will auto-show the right ones based on currency/region.
        form.add("payment_method_types[]", "card");
        form.add("payment_method_types[]", "blik");
        form.add("payment_method_types[]", "p24");

        int idx = 0;
        for (OrderLine l : order.getLines()) {
            String prefix = "line_items[" + idx + "]";
            form.add(prefix + "[quantity]", String.valueOf(l.getQuantity()));
            form.add(prefix + "[price_data][currency]", order.getCurrency().toLowerCase());
            form.add(prefix + "[price_data][product_data][name]", l.getNameSnapshot());
            form.add(prefix + "[price_data][unit_amount]",
                    String.valueOf(toMinorUnits(l.getUnitPrice())));
            idx++;
        }
        if (order.getDeliveryPrice() != null && order.getDeliveryPrice().signum() > 0) {
            String prefix = "line_items[" + idx + "]";
            form.add(prefix + "[quantity]", "1");
            form.add(prefix + "[price_data][currency]", order.getCurrency().toLowerCase());
            form.add(prefix + "[price_data][product_data][name]",
                    "Delivery (" + order.getShipmentMethod().name() + ")");
            form.add(prefix + "[price_data][unit_amount]",
                    String.valueOf(toMinorUnits(order.getDeliveryPrice())));
        }
        form.add("metadata[orderReference]", order.getReference());
        form.add("metadata[shipmentMethod]", order.getShipmentMethod().name());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> session = stripeRest.post()
                    .uri("/v1/checkout/sessions")
                    .header("Authorization", "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (session == null || session.get("url") == null) {
                throw new IllegalStateException("Stripe returned an empty session");
            }
            order.setStripeSessionId(String.valueOf(session.get("id")));
            orderRepository.save(order);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("url", session.get("url"));
            out.put("sessionId", session.get("id"));
            out.put("mock", false);
            out.put("reference", reference);
            return out;
        } catch (HttpClientErrorException ex) {
            log.error("Stripe Checkout Session creation failed: {}", ex.getResponseBodyAsString());
            throw new BadRequestException("Payment provider error. Please try again.");
        } catch (Exception ex) {
            log.error("Stripe call failed", ex);
            throw new BadRequestException("Payment is temporarily unavailable.");
        }
    }

    /**
     * Mock-mode counterpart of the Stripe webhook: the mock-pay page POSTs here to mark
     * the order as paid. Safe in dev only — disabled when a real Stripe key is set.
     */
    @Transactional
    public Map<String, Object> mockComplete(String reference) {
        if (!isMock()) {
            throw new BadRequestException("Mock payment is disabled when Stripe is configured.");
        }
        CustomerOrder order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStripePaymentIntent("mock_" + System.currentTimeMillis());
            order = orderService.transition(order, OrderStatus.PAID, "Payment confirmed");
            dispatchService.handlePayment(order);
        }
        return Map.of("ok", true, "reference", reference, "status", order.getStatus().name());
    }

    /**
     * Stripe webhook: lightweight handler that updates the matched order on
     * {@code checkout.session.completed}. Signature verification is best-effort:
     * if {@code app.stripe.webhook-secret} is configured we'd verify here; for now
     * the live deployment should sit behind an authenticated gateway.
     */
    @Transactional
    public Map<String, Object> handleStripeEvent(Map<String, Object> event) {
        String type = String.valueOf(event.getOrDefault("type", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        if (data == null) return Map.of("ok", false, "reason", "missing data");
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) data.get("object");
        if (object == null) return Map.of("ok", false, "reason", "missing object");

        String reference = (String) object.get("client_reference_id");
        if (reference == null) {
            // Older Stripe events store it in metadata.
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) object.get("metadata");
            if (meta != null) reference = (String) meta.get("orderReference");
        }
        if (reference == null) return Map.of("ok", false, "reason", "no order reference");

        CustomerOrder order = orderRepository.findByReference(reference).orElse(null);
        if (order == null) return Map.of("ok", false, "reason", "order not found");

        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    Object pi = object.get("payment_intent");
                    if (pi != null) order.setStripePaymentIntent(String.valueOf(pi));
                    order = orderService.transition(order, OrderStatus.PAID, "Payment confirmed");
                    dispatchService.handlePayment(order);
                }
            }
            case "checkout.session.expired", "checkout.session.async_payment_failed" -> {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    orderService.transition(order, OrderStatus.FAILED, "Payment was not completed");
                }
            }
            default -> { /* ignore */ }
        }
        return Map.of("ok", true, "type", type, "reference", reference);
    }

    private String buildSuccessUrl(String reference) {
        // Allow {REF} or {orderId} placeholders so existing config keeps working.
        return successUrlTemplate
                .replace("{REF}", reference)
                .replace("{orderId}", reference);
    }

    private static long toMinorUnits(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private static String trimTrailing(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // Helper kept for future expansion (e.g. refund endpoints).
    @SuppressWarnings("unused")
    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + secretKey);
        return h;
    }
}
