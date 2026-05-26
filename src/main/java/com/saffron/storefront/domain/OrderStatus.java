package com.saffron.storefront.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    READY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    FAILED
}
