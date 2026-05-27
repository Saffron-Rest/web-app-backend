package com.saffron.storefront.controller;

import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> summary() {
        AuthHelper.currentUser();
        return service.summary();
    }
}
