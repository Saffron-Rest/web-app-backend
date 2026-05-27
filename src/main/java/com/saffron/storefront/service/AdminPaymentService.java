package com.saffron.storefront.service;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.repository.OrderEventRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payment-related admin actions: mark an order paid offline (bank transfer, cash on
 * pickup) and refund (Stripe API when configured, manual otherwise).
 *
 * <p>Kept separate from the customer-facing {@link PaymentService} so the public
 * Stripe webhook & mock-pay path stay tidy.
 */
@Service
public class AdminPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AdminPaymentService.class);
    private static final String STRIPE_API = "https://api.stripe.com";

    private final CustomerOrderRepository orders;
    private final OrderEventRepository events;
    private final OrderService orderService;
    private final DeliveryDispatchService dispatchService;
    private final StorefrontAuditService audit;
    private final String stripeSecretKey;
    private final RestClient stripeRest;

    public AdminPaymentService(CustomerOrderRepository orders,
                                OrderEventRepository events,
                                OrderService orderService,
                                DeliveryDispatchService dispatchService,
                                StorefrontAuditService audit,
                                @Value("${app.stripe.secret-key:}") String stripeSecretKey) {
        this.orders = orders;
        this.events = events;
        this.orderService = orderService;
        this.dispatchService = dispatchService;
        this.audit = audit;
        this.stripeSecretKey = stripeSecretKey == null ? "" : stripeSecretKey.trim();
        this.stripeRest = RestClient.builder().baseUrl(STRIPE_API).build();
    }

    public boolean stripeConfigured() {
        return !stripeSecretKey.isBlank();
    }

    /**
     * Manually flip a PENDING_PAYMENT order to PAID. Use for bank transfers, cash on
     * pickup, etc. — anything that bypasses Stripe. Dispatches delivery as if the
     * customer had paid online.
     */
    @Transactional
    public Map<String, Object> markPaid(String reference, String method, String note,
                                         HttpServletRequest http) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                && order.getStatus() != OrderStatus.FAILED) {
            throw new BadRequestException("Order is already " + order.getStatus().name());
        }
        String label = (method == null || method.isBlank()) ? "manual" : method.trim();
        String msg = "Marked paid (" + label + ")"
                + (note != null && !note.isBlank() ? " — " + note.trim() : "");
        order.setStripePaymentIntent("offline_" + label.toLowerCase().replaceAll("\\s+", "-")
                + "_" + System.currentTimeMillis());
        order = orderService.transition(order, OrderStatus.PAID, msg);
        dispatchService.handlePayment(order);
        audit.record("ORDER_MARK_PAID", "CustomerOrder", order.getId(),
                "reference=" + reference + ",method=" + label, http);
        return OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    /**
     * Issue a refund. If Stripe is configured AND the order has a payment intent,
     * we call Stripe's Refunds API and store the {@code re_xxx} id. Otherwise we
     * record a manual refund (e.g. cash refund). In both cases the order
     * transitions to {@link OrderStatus#REFUNDED}.
     *
     * @param amount  optional — if null, refund the full {@code total}.
     * @param reason  Stripe's reason code or a free-text justification.
     */
    @Transactional
    public Map<String, Object> refund(String reference, BigDecimal amount, String reason,
                                       HttpServletRequest http) {
        CustomerOrder order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new BadRequestException("Order already refunded");
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Order is not paid — cancel it instead");
        }
        BigDecimal refundAmt = amount == null
                ? order.getTotal()
                : amount.setScale(2, RoundingMode.HALF_UP);
        if (refundAmt.signum() <= 0) {
            throw new BadRequestException("Refund amount must be positive");
        }
        if (refundAmt.compareTo(order.getTotal()) > 0) {
            throw new BadRequestException("Refund cannot exceed order total");
        }

        String refundRef;
        boolean live = false;
        if (stripeConfigured() && order.getStripePaymentIntent() != null
                && !order.getStripePaymentIntent().startsWith("offline_")
                && !order.getStripePaymentIntent().startsWith("mock_")) {
            refundRef = callStripeRefund(order.getStripePaymentIntent(), refundAmt, reason);
            live = true;
        } else {
            // Manual refund — cash back, bank wire, etc. Synthesise an id so the
            // admin UI has something stable to display + dedupe on.
            refundRef = "manual-" + System.currentTimeMillis();
        }

        order.setRefundedAmount(refundAmt);
        order.setRefundReference(refundRef);
        order.setRefundedAt(Instant.now());
        String reasonLabel = (reason == null || reason.isBlank()) ? "no reason given" : reason.trim();
        String msg = "Refunded " + refundAmt + " " + order.getCurrency()
                + (live ? " via Stripe (" : " manually (")
                + reasonLabel + ")";
        order = orderService.transition(order, OrderStatus.REFUNDED, msg);
        audit.record("ORDER_REFUND", "CustomerOrder", order.getId(),
                "reference=" + reference + ",amount=" + refundAmt
                        + ",ref=" + refundRef + ",live=" + live, http);

        Map<String, Object> body = new LinkedHashMap<>(
                OrderService.toMap(order, events.findByOrderIdOrderByCreatedAtAsc(order.getId())));
        body.put("refundLive", live);
        body.put("refundReference", refundRef);
        return body;
    }

    private String callStripeRefund(String paymentIntent, BigDecimal amount, String reason) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntent);
        // Stripe expects amount in minor units (groszy for PLN).
        form.add("amount", String.valueOf(amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact()));
        // Stripe only accepts a small set of reason codes; everything else we drop into metadata.
        if (reason != null && !reason.isBlank()) {
            String r = reason.trim().toLowerCase();
            if (r.equals("duplicate") || r.equals("fraudulent") || r.equals("requested_by_customer")) {
                form.add("reason", r);
            } else {
                form.add("metadata[reason]", reason.trim());
            }
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = stripeRest.post()
                    .uri("/v1/refunds")
                    .header("Authorization", "Bearer " + stripeSecretKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("id") == null) {
                throw new IllegalStateException("Stripe returned empty refund response");
            }
            return String.valueOf(resp.get("id"));
        } catch (HttpClientErrorException ex) {
            log.error("Stripe refund failed: {}", ex.getResponseBodyAsString());
            throw new BadRequestException("Refund failed: " + ex.getStatusText());
        } catch (Exception ex) {
            log.error("Stripe refund call failed", ex);
            throw new BadRequestException("Refund provider unavailable. Please try again.");
        }
    }
}
