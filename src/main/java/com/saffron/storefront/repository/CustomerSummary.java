package com.saffron.storefront.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Customer aggregate computed from {@code customer_orders}. We never built a
 * dedicated Customer table — orders are the source of truth and we just
 * group by email when the admin asks for a customers list.
 */
public record CustomerSummary(
        String email,
        String name,
        String phone,
        Long orderCount,
        Instant lastOrderAt,
        BigDecimal totalSpent
) {}
