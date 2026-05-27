package com.saffron.storefront.security;

import com.saffron.storefront.domain.AdminRole;

/** Principal carried in the Spring SecurityContext for the duration of a request. */
public record AuthUser(
        String id,
        String email,
        String name,
        AdminRole role,
        boolean mustChangePassword
) {}
