package com.saffron.storefront.domain;

public enum ShipmentMethod {
    INSTANT_WOLT,
    INSTANT_GLOVO,
    COURIER_DHL,
    COURIER_DPD,
    /** Pickup at the restaurant — no carrier involved. */
    PICKUP
}
