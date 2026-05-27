package com.saffron.storefront.domain;

/**
 * Storefront admin roles — completely independent of the cashflow platform's
 * own role hierarchy. ADMIN holds everything; MANAGER can manage day-to-day
 * (orders, reservations, products); STAFF is read-only.
 */
public enum AdminRole {
    ADMIN,
    MANAGER,
    STAFF;

    public boolean canManageCatalog() {
        return this == ADMIN || this == MANAGER;
    }

    public boolean canManageOrders() {
        return this == ADMIN || this == MANAGER;
    }

    public boolean canManageUsers() {
        return this == ADMIN;
    }
}
