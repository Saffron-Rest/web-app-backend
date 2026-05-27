package com.saffron.storefront.controller;

import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminCustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final AdminCustomerService service;

    public AdminCustomerController(AdminCustomerService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        AuthHelper.currentUser();
        return service.list(q, page, size);
    }
}
