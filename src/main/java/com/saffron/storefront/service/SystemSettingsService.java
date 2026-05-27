package com.saffron.storefront.service;

import com.saffron.storefront.domain.SystemSetting;
import com.saffron.storefront.repository.SystemSettingRepository;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.security.AuthUser;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralised read/write for runtime-editable configuration. Hot-cached in memory so
 * every request hitting the storefront doesn't issue a DB roundtrip — the cache is
 * invalidated on every save and rebuilt lazily on the next read.
 *
 * <p>Setting keys live in {@link Key}. Add new keys by extending that class and
 * (optionally) adding a default in {@link #defaultFor}.
 */
@Service
public class SystemSettingsService {

    /** All known keys live here so we can render them in the admin UI. */
    public static final class Key {
        /** When false, POST /api/orders returns 503. */
        public static final String ACCEPTING_ORDERS = "storefront.acceptingOrders";
        /** When false, POST /api/reservations returns 503. */
        public static final String ACCEPTING_RESERVATIONS = "storefront.acceptingReservations";
        /** Free-text banner shown at the top of the public storefront. Blank = no banner. */
        public static final String BANNER_MESSAGE = "storefront.bannerMessage";
        /** Either "info" / "warning" / "success". Controls banner styling. */
        public static final String BANNER_TONE = "storefront.bannerTone";
        /** Customer-facing minutes added to ETAs when the kitchen is slammed. */
        public static final String PREP_DELAY_MINUTES = "storefront.prepDelayMinutes";
        private Key() {}
    }

    private static final Map<String, String> DEFAULTS = Map.of(
            Key.ACCEPTING_ORDERS, "true",
            Key.ACCEPTING_RESERVATIONS, "true",
            Key.BANNER_MESSAGE, "",
            Key.BANNER_TONE, "info",
            Key.PREP_DELAY_MINUTES, "0"
    );

    private final SystemSettingRepository repo;
    /**
     * Stores cached string values; {@code null} marker (empty string written when DB
     * has no row) avoids null-key issues. The cache is built once and invalidated on
     * write.
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public SystemSettingsService(SystemSettingRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    void warmCache() {
        loadFromDb();
    }

    private synchronized void loadFromDb() {
        cache.clear();
        for (SystemSetting s : repo.findAll()) {
            cache.put(s.getKey(), s.getValue() == null ? "" : s.getValue());
        }
        loaded = true;
    }

    private void ensureLoaded() {
        if (!loaded) loadFromDb();
    }

    /** Lower-level string getter; falls back to {@link #defaultFor(String)}. */
    public String getString(String key) {
        ensureLoaded();
        String v = cache.get(key);
        if (v == null || v.isEmpty()) {
            // We treat empty as "use the default" so a saved empty banner shows nothing
            // (banner default is also empty) and a saved empty toggle falls back to true.
            if (cache.containsKey(key)) return v == null ? "" : v;
            return defaultFor(key);
        }
        return v;
    }

    /** True iff the saved string parses as a "truthy" boolean — "true"/"yes"/"1". */
    public boolean getBoolean(String key) {
        String v = getString(key);
        if (v == null) return false;
        return switch (v.trim().toLowerCase()) {
            case "true", "yes", "y", "on", "1" -> true;
            default -> false;
        };
    }

    public int getInt(String key, int fallback) {
        try {
            String v = getString(key);
            return (v == null || v.isBlank()) ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Persists the new value and invalidates the cache. Records the actor when available.
     *  {@code updatedAt} is refreshed by {@link SystemSetting}'s {@code @PreUpdate} hook. */
    @Transactional
    public void set(String key, String value) {
        SystemSetting s = repo.findById(key).orElseGet(() -> new SystemSetting(key, ""));
        s.setKey(key);
        s.setValue(value == null ? "" : value);
        AuthUser u = AuthHelper.currentUserOrNull();
        s.setUpdatedByEmail(u == null ? null : u.email());
        repo.save(s);
        cache.put(key, value == null ? "" : value);
    }

    /** Returns the full effective map (DB value or default per key). For the admin UI. */
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot() {
        ensureLoaded();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            String key = e.getKey();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", getString(key));
            entry.put("default", e.getValue());
            entry.put("hasOverride", cache.containsKey(key));
            out.put(key, entry);
        }
        return out;
    }

    public static String defaultFor(String key) {
        return DEFAULTS.getOrDefault(key, "");
    }
}
