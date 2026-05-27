package com.saffron.storefront.service;

import com.saffron.storefront.domain.AdminUser;
import com.saffron.storefront.repository.AdminUserRepository;
import com.saffron.storefront.security.AuthUser;
import com.saffron.storefront.security.JwtService;
import com.saffron.storefront.security.UnauthorizedException;
import com.saffron.storefront.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final StorefrontAuditService audit;

    public AuthService(AdminUserRepository users, PasswordEncoder encoder,
                       JwtService jwt, StorefrontAuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> login(String email, String password, HttpServletRequest req) {
        if (email == null || password == null) throw new BadRequestException("Email and password required");
        AdminUser u = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!u.isActive()) throw new UnauthorizedException("Account is disabled");
        if (!encoder.matches(password, u.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        u.setLastLoginAt(Instant.now());
        users.save(u);
        AuthUser principal = toPrincipal(u);
        String token = jwt.generateToken(principal);
        audit.record(principal, "AUTH_LOGIN", "AdminUser", u.getId(), null, req);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("user", userJson(u));
        return body;
    }

    @Transactional
    public Map<String, Object> changePassword(String currentPassword, String newPassword,
                                              AuthUser principal, HttpServletRequest req) {
        if (newPassword == null || newPassword.length() < 10) {
            throw new BadRequestException("New password must be at least 10 characters");
        }
        AdminUser u = users.findById(principal.id())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        if (!encoder.matches(currentPassword, u.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
        audit.record(principal, "AUTH_CHANGE_PASSWORD", "AdminUser", u.getId(), null, req);
        AuthUser refreshed = toPrincipal(u);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", jwt.generateToken(refreshed));
        body.put("user", userJson(u));
        return body;
    }

    public static AuthUser toPrincipal(AdminUser u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isMustChangePassword());
    }

    public static Map<String, Object> userJson(AdminUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("name", u.getName());
        m.put("role", u.getRole().name());
        m.put("active", u.isActive());
        m.put("mustChangePassword", u.isMustChangePassword());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }
}
