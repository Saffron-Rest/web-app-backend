package com.saffron.storefront.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Generic runtime-editable configuration. One row per setting key. Values are stored
 * as text — {@link com.saffron.storefront.service.SystemSettingsService} provides
 * typed getters/setters on top.
 *
 * <p>The intent is "knobs the operator can turn without redeploying": storefront
 * pause toggles, customer-facing banner, etc. Secrets (Stripe keys, carrier
 * credentials) intentionally stay in env vars and are NOT mirrored here.
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 100, nullable = false, updatable = false)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_by_email", length = 200)
    private String updatedByEmail;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public SystemSetting() {}

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUpdatedByEmail() { return updatedByEmail; }
    public void setUpdatedByEmail(String e) { this.updatedByEmail = e; }
    public Instant getUpdatedAt() { return updatedAt; }
}
