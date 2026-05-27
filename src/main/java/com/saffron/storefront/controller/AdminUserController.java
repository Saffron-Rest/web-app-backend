package com.saffron.storefront.controller;

import com.saffron.storefront.domain.AdminRole;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    public record CreateRequest(String email, String name, AdminRole role) {}
    public record UpdateRequest(String name, AdminRole role, Boolean active) {}

    @GetMapping
    public List<Map<String, Object>> list() {
        AuthHelper.requireUserManager();
        return service.list();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateRequest req, HttpServletRequest http) {
        AuthHelper.requireUserManager();
        return service.create(req.email(), req.name(), req.role(), http);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody UpdateRequest req,
                                      HttpServletRequest http) {
        AuthHelper.requireUserManager();
        return service.update(id, req.name(), req.role(), req.active(), http);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable String id, HttpServletRequest http) {
        AuthHelper.requireUserManager();
        return service.resetPassword(id, http);
    }
}
