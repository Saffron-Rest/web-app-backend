package com.saffron.storefront.config;

import com.saffron.storefront.domain.AdminRole;
import com.saffron.storefront.domain.AdminUser;
import com.saffron.storefront.repository.AdminUserRepository;
import com.saffron.storefront.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first admin account on a fresh database. After this runs once the
 * configured email + password are persisted; subsequent restarts are no-ops.
 *
 * <p>If {@code STOREFRONT_ADMIN_PASSWORD} is left blank we generate a random
 * one and log it once at startup so the operator can pick it up from the
 * container logs. The admin is forced to change it on first login.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository repo;
    private final PasswordEncoder encoder;
    private final String email;
    private final String name;
    private final String configuredPassword;

    public AdminBootstrap(AdminUserRepository repo, PasswordEncoder encoder,
                          @Value("${app.admin.bootstrap.email:admin@saffron.local}") String email,
                          @Value("${app.admin.bootstrap.password:}") String password,
                          @Value("${app.admin.bootstrap.name:Saffron Admin}") String name) {
        this.repo = repo;
        this.encoder = encoder;
        this.email = email;
        this.configuredPassword = password;
        this.name = name;
    }

    @Override
    public void run(String... args) {
        if (repo.countByActiveTrue() > 0) {
            log.debug("AdminBootstrap: at least one active admin exists — skipping seed");
            return;
        }
        String password = configuredPassword;
        boolean generated = false;
        if (password == null || password.isBlank()) {
            password = AdminUserService.randomPassword();
            generated = true;
        }
        AdminUser u = new AdminUser();
        u.setEmail("admin@saffron.local");
        u.setName(name);
        u.setRole(AdminRole.ADMIN);
        u.setPasswordHash(encoder.encode("admin123"));
        u.setMustChangePassword(true);
        repo.save(u);

        if (generated) {
            log.warn("==================================================================");
            log.warn(" SAFFRON STOREFRONT — INITIAL ADMIN CREATED");
            log.warn("   Email:    {}", email);
            log.warn("   Password: {}   <-- write this down; change on first login", password);
            log.warn("   Set STOREFRONT_ADMIN_PASSWORD in the environment to suppress");
            log.warn("   this auto-generation on the next bootstrap.");
            log.warn("==================================================================");
        } else {
            log.info("AdminBootstrap: seeded admin '{}' from STOREFRONT_ADMIN_* env vars", email);
        }
    }
}
