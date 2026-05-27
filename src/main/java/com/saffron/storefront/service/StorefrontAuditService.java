package com.saffron.storefront.service;

import com.saffron.storefront.domain.StorefrontAuditLog;
import com.saffron.storefront.repository.StorefrontAuditLogRepository;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorefrontAuditService {

    private final StorefrontAuditLogRepository repo;

    public StorefrontAuditService(StorefrontAuditLogRepository repo) { this.repo = repo; }

    @Transactional
    public void record(String action, String entity, String entityId, String details, HttpServletRequest req) {
        record(AuthHelper.currentUserOrNull(), action, entity, entityId, details, req);
    }

    @Transactional
    public void record(AuthUser actor, String action, String entity, String entityId,
                       String details, HttpServletRequest req) {
        StorefrontAuditLog log = new StorefrontAuditLog();
        if (actor != null) {
            log.setUserId(actor.id());
            log.setUserEmail(actor.email());
        }
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setIp(extractIp(req));
        repo.save(log);
    }

    private static String extractIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
