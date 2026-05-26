package com.saffron.storefront.service;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.domain.ShipmentMethod;
import com.saffron.storefront.repository.CustomerOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * After a customer pays, this service:
 * <ol>
 *   <li>Books delivery with the chosen carrier (mocked for now — real carrier dispatch
 *       slots in here by calling the existing {@code QuoteProvider} adapters' "book"
 *       endpoints).</li>
 *   <li>Records initial tracking info + estimated ready/delivery times on the order.</li>
 *   <li>For demo/dev: a scheduled task progresses paid orders through the kitchen and
 *       carrier stages so the customer's confirmation page animates through statuses.
 *       In production with carrier webhooks this scheduler stays disabled.</li>
 * </ol>
 */
@Service
public class DeliveryDispatchService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatchService.class);

    private final CustomerOrderRepository orderRepository;
    private final OrderService orderService;
    private final boolean progressionEnabled;
    private final int preparingAfterSec;
    private final int readyAfterSec;
    private final int outAfterSec;
    private final int deliveredAfterSec;

    public DeliveryDispatchService(
            CustomerOrderRepository orderRepository,
            OrderService orderService,
            @Value("${app.delivery.progression.enabled:true}") boolean progressionEnabled,
            @Value("${app.delivery.progression.preparing-after-seconds:5}") int preparingAfterSec,
            @Value("${app.delivery.progression.ready-after-seconds:25}") int readyAfterSec,
            @Value("${app.delivery.progression.out-after-seconds:35}") int outAfterSec,
            @Value("${app.delivery.progression.delivered-after-seconds:90}") int deliveredAfterSec) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.progressionEnabled = progressionEnabled;
        this.preparingAfterSec = preparingAfterSec;
        this.readyAfterSec = readyAfterSec;
        this.outAfterSec = outAfterSec;
        this.deliveredAfterSec = deliveredAfterSec;
    }

    /** Called immediately after PaymentService marks the order PAID. */
    @Transactional
    public void handlePayment(CustomerOrder order) {
        ShipmentMethod method = order.getShipmentMethod();
        Instant now = Instant.now();

        // Book delivery (mocked carrier response).
        String trackingCode = generateTrackingCode(method);
        String trackingUrl = mockTrackingUrl(method, trackingCode);
        order.setTrackingCode(trackingCode);
        order.setTrackingUrl(trackingUrl);

        Instant ready = now.plus(readyAfterSec, ChronoUnit.SECONDS);
        Instant delivered = now.plus(deliveredAfterSec, ChronoUnit.SECONDS);
        order.setEstimatedReadyAt(ready);
        if (method != ShipmentMethod.PICKUP) {
            order.setEstimatedDeliveryAt(delivered);
        }
        orderRepository.save(order);

        String msg = switch (method) {
            case INSTANT_WOLT -> "Wolt courier booked";
            case INSTANT_GLOVO -> "Glovo courier booked";
            case COURIER_DHL -> "DHL shipment created";
            case COURIER_DPD -> "DPD shipment created";
            case PICKUP -> "Pickup confirmed — we'll prepare your order";
        };
        orderService.appendEvent(order, OrderStatus.PAID, msg, trackingCode, trackingUrl);
        log.info("Dispatched order {} via {} (tracking={})", order.getReference(), method, trackingCode);
    }

    /**
     * Demo-only: every few seconds, push paid orders along the kitchen / carrier timeline.
     * Disabled by setting {@code app.delivery.progression.enabled=false} when real carrier
     * webhooks are wired up.
     */
    @Scheduled(fixedDelay = 4000)
    @Transactional
    public void progressOrders() {
        if (!progressionEnabled) return;
        List<CustomerOrder> active = orderRepository.findByStatusIn(java.util.List.of(
                OrderStatus.PAID,
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.OUT_FOR_DELIVERY));

        Instant now = Instant.now();
        for (CustomerOrder o : active) {
            long sincePayment = ChronoUnit.SECONDS.between(o.getUpdatedAt(), now);
            switch (o.getStatus()) {
                case PAID -> {
                    if (sincePayment >= preparingAfterSec) {
                        orderService.transition(o, OrderStatus.PREPARING, "Chef started preparing");
                    }
                }
                case PREPARING -> {
                    if (sincePayment >= (readyAfterSec - preparingAfterSec)) {
                        if (o.getShipmentMethod() == ShipmentMethod.PICKUP) {
                            orderService.transition(o, OrderStatus.READY, "Ready for pickup");
                        } else {
                            orderService.transition(o, OrderStatus.READY, "Packed and ready");
                        }
                    }
                }
                case READY -> {
                    if (o.getShipmentMethod() == ShipmentMethod.PICKUP) {
                        // Stays in READY until the customer picks it up (no auto-progress).
                        continue;
                    }
                    if (sincePayment >= (outAfterSec - readyAfterSec)) {
                        orderService.transition(o, OrderStatus.OUT_FOR_DELIVERY,
                                carrierHandoffMessage(o.getShipmentMethod()));
                    }
                }
                case OUT_FOR_DELIVERY -> {
                    if (sincePayment >= (deliveredAfterSec - outAfterSec)) {
                        orderService.transition(o, OrderStatus.DELIVERED, "Delivered. Enjoy!");
                    }
                }
                default -> { /* terminal */ }
            }
        }
    }

    private static String carrierHandoffMessage(ShipmentMethod method) {
        return switch (method) {
            case INSTANT_WOLT -> "Wolt courier picked up your order";
            case INSTANT_GLOVO -> "Glovo courier picked up your order";
            case COURIER_DHL -> "Handed to DHL Express";
            case COURIER_DPD -> "Handed to DPD";
            case PICKUP -> "Ready for pickup";
        };
    }

    private static String generateTrackingCode(ShipmentMethod method) {
        String prefix = switch (method) {
            case INSTANT_WOLT -> "WOLT";
            case INSTANT_GLOVO -> "GLV";
            case COURIER_DHL -> "DHL";
            case COURIER_DPD -> "DPD";
            case PICKUP -> "PKP";
        };
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String mockTrackingUrl(ShipmentMethod method, String code) {
        return switch (method) {
            case INSTANT_WOLT -> "https://wolt.com/track/" + code;
            case INSTANT_GLOVO -> "https://glovoapp.com/track/" + code;
            case COURIER_DHL -> "https://www.dhl.com/track?awb=" + code;
            case COURIER_DPD -> "https://tracktrace.dpd.com.pl/findParcel?p1=" + code;
            case PICKUP -> null;
        };
    }
}
