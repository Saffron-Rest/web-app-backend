package com.saffron.storefront.service;

import com.saffron.storefront.domain.AdminRole;
import com.saffron.storefront.domain.AdminUser;
import com.saffron.storefront.repository.AdminUserRepository;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserService {

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final AdminUserRepository repo;
    private final PasswordEncoder encoder;
    private final StorefrontAuditService audit;

    public AdminUserService(AdminUserRepository repo, PasswordEncoder encoder, StorefrontAuditService audit) {
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        return repo.findAllByOrderByCreatedAtAsc().stream()
                .map(AuthService::userJson).toList();
    }

    @Transactional
    public Map<String, Object> create(String email, String name, AdminRole role, HttpServletRequest req) {
        if (email == null || email.isBlank()) throw new BadRequestException("Email required");
        if (name == null || name.isBlank()) throw new BadRequestException("Name required");
        if (role == null) throw new BadRequestException("Role required");
        if (repo.findByEmailIgnoreCase(email.trim()).isPresent()) {
            throw new BadRequestException("Email already in use");
        }
        AdminUser u = new AdminUser();
        u.setEmail(email.trim());
        u.setName(name.trim());
        u.setRole(role);
        String tempPwd = randomPassword();
        u.setPasswordHash(encoder.encode(tempPwd));
        u.setMustChangePassword(true);
        repo.save(u);
        audit.record("ADMIN_USER_CREATE", "AdminUser", u.getId(), "email=" + u.getEmail(), req);
        Map<String, Object> out = new java.util.LinkedHashMap<>(AuthService.userJson(u));
        out.put("temporaryPassword", tempPwd);
        return out;
    }

    @Transactional
    public Map<String, Object> update(String id, String name, AdminRole role, Boolean active,
                                      HttpServletRequest req) {
        AdminUser u = repo.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        if (name != null && !name.isBlank()) u.setName(name.trim());
        if (role != null) {
            if (u.getId().equals(AuthHelper.currentUser().id()) && role != AdminRole.ADMIN) {
                throw new BadRequestException("Cannot demote yourself");
            }
            u.setRole(role);
        }
        if (active != null) {
            if (u.getId().equals(AuthHelper.currentUser().id()) && !active) {
                throw new BadRequestException("Cannot deactivate yourself");
            }
            u.setActive(active);
        }
        repo.save(u);
        audit.record("ADMIN_USER_UPDATE", "AdminUser", u.getId(), null, req);
        return AuthService.userJson(u);
    }

    @Transactional
    public Map<String, Object> resetPassword(String id, HttpServletRequest req) {
        AdminUser u = repo.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        String tempPwd = randomPassword();
        u.setPasswordHash(encoder.encode(tempPwd));
        u.setMustChangePassword(true);
        repo.save(u);
        audit.record("ADMIN_USER_RESET_PASSWORD", "AdminUser", u.getId(), null, req);
        return Map.of("temporaryPassword", tempPwd);
    }

    public static String randomPassword() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
