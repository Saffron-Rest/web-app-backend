package com.saffron.storefront.controller;

import com.saffron.storefront.domain.StorefrontAuditLog;
import com.saffron.storefront.repository.StorefrontAuditLogRepository;
import com.saffron.storefront.security.AuthHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {

    private final StorefrontAuditLogRepository repo;

    public AdminAuditController(StorefrontAuditLogRepository repo) { this.repo = repo; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "100") int size) {
        AuthHelper.requireAdmin();
        Page<StorefrontAuditLog> p = repo.search(
                (q == null || q.isBlank()) ? null : q.trim(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 500))));
        List<Map<String, Object>> items = p.getContent().stream().map(AdminAuditController::toMap).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p.getNumber());
        body.put("size", p.getSize());
        body.put("totalElements", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        return body;
    }

    private static Map<String, Object> toMap(StorefrontAuditLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("createdAt", l.getCreatedAt());
        m.put("userEmail", l.getUserEmail());
        m.put("action", l.getAction());
        m.put("entity", l.getEntity());
        m.put("entityId", l.getEntityId());
        m.put("details", l.getDetails());
        m.put("ip", l.getIp());
        return m;
    }
}
