package com.saffron.storefront.service;

import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.repository.CustomerSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminCustomerService {

    private final CustomerOrderRepository orders;

    public AdminCustomerService(CustomerOrderRepository orders) { this.orders = orders; }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String q, int page, int size) {
        Page<CustomerSummary> p = orders.findCustomers(
                (q == null || q.isBlank()) ? null : q.trim(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
        List<Map<String, Object>> items = p.getContent().stream().map(AdminCustomerService::toMap).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p.getNumber());
        body.put("size", p.getSize());
        body.put("totalElements", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        return body;
    }

    private static Map<String, Object> toMap(CustomerSummary c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", c.email());
        m.put("name", c.name());
        m.put("phone", c.phone());
        m.put("orderCount", c.orderCount());
        m.put("lastOrderAt", c.lastOrderAt());
        m.put("totalSpent", c.totalSpent());
        return m;
    }
}
