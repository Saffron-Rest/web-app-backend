package com.saffron.storefront.security;

import com.saffron.storefront.domain.AdminRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthHelper {

    private AuthHelper() {}

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return user;
    }

    public static AuthUser currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) return null;
        return user;
    }

    public static boolean isAdmin()   { return currentUser().role() == AdminRole.ADMIN; }
    public static boolean isManager() { return currentUser().role() == AdminRole.MANAGER; }

    public static void requireAdmin() {
        if (!isAdmin()) throw new ForbiddenException("Admin only");
    }

    public static void requireCatalogWriter() {
        if (!currentUser().role().canManageCatalog()) throw new ForbiddenException("Not allowed");
    }

    public static void requireOrderWriter() {
        if (!currentUser().role().canManageOrders()) throw new ForbiddenException("Not allowed");
    }

    public static void requireUserManager() {
        if (!currentUser().role().canManageUsers()) throw new ForbiddenException("Not allowed");
    }
}
