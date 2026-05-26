package com.saffron.storefront.controller;

import com.saffron.storefront.service.OrderService;
import com.saffron.storefront.service.OrderService.CreateOrderRequest;
import com.saffron.storefront.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class PublicOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public PublicOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    /** Create an order. Returns the order incl. reference; payment is initiated separately. */
    @PostMapping
    public Map<String, Object> create(@RequestBody CreateOrderRequest req) {
        return orderService.create(req);
    }

    /** Read an order back by its short reference (used by the order confirmation page). */
    @GetMapping("/{reference}")
    public Map<String, Object> byReference(@PathVariable String reference) {
        return orderService.getByReference(reference);
    }

    /**
     * Begin the payment flow: returns the URL to redirect the customer to.
     * In real Stripe mode this is a hosted Checkout Session URL. In mock mode
     * it's a local {@code /order/{ref}/mock-pay} page used by the dev environment.
     */
    @PostMapping("/{reference}/checkout")
    public Map<String, Object> initiateCheckout(@PathVariable String reference) {
        return paymentService.initiateCheckout(reference);
    }

    /**
     * Dev-mode only: the mock-pay page calls this to flip the order to PAID and
     * kick off delivery dispatch. Disabled when {@code STRIPE_SECRET_KEY} is set.
     */
    @PostMapping("/{reference}/payment/mock-complete")
    public Map<String, Object> mockComplete(@PathVariable String reference) {
        return paymentService.mockComplete(reference);
    }

    /** Stripe webhook endpoint. Stripe POSTs JSON events here once configured. */
    @PostMapping("/stripe-webhook")
    public ResponseEntity<Map<String, Object>> stripeWebhook(@RequestBody Map<String, Object> event) {
        return ResponseEntity.ok(paymentService.handleStripeEvent(event));
    }

    /** Returns the publishable Stripe key + mock flag — used by the frontend payment screen. */
    @GetMapping("/payments/config")
    public Map<String, Object> paymentConfig() {
        return Map.of(
                "mock", paymentService.isMock(),
                "publishableKey", paymentService.isMock() ? null : "configured");
    }
}
