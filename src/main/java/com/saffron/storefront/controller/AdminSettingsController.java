package com.saffron.storefront.controller;

import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.StorefrontAuditService;
import com.saffron.storefront.service.SystemSettingsService;
import com.saffron.storefront.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final SystemSettingsService settings;
    private final StorefrontAuditService audit;

    public AdminSettingsController(SystemSettingsService settings, StorefrontAuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> all() {
        AuthHelper.currentUser();
        return settings.snapshot();
    }

    /** Bulk update — body is { key: value } for any subset of known keys. */
    @PatchMapping
    public Map<String, Object> patch(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        AuthHelper.requireAdmin();
        if (body == null || body.isEmpty()) throw new BadRequestException("Empty body");
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
            // Don't accept arbitrary keys — only ones we know about (defaults map).
            if (!SystemSettingsService.defaultFor(key).isEmpty() || isKnownEmptyDefault(key)) {
                settings.set(key, value);
                changed.put(key, value);
            }
        }
        audit.record("SETTINGS_UPDATE", "SystemSetting", null,
                "keys=" + String.join(",", changed.keySet()), http);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("updated", changed);
        resp.put("snapshot", settings.snapshot());
        return resp;
    }

    /**
     * Some keys legitimately default to an empty string (e.g. banner message).
     * {@code defaultFor("")} returning empty doesn't mean "unknown", so guard here.
     */
    private static boolean isKnownEmptyDefault(String key) {
        return key.equals(SystemSettingsService.Key.BANNER_MESSAGE)
                || key.equals(SystemSettingsService.Key.BANNER_TONE);
    }
}
