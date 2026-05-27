package com.saffron.storefront.controller;

import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AdminCatalogService;
import com.saffron.storefront.service.AdminCatalogService.ProductPayload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminCatalogService service;

    public AdminProductController(AdminCatalogService service) { this.service = service; }

    @GetMapping
    public List<Map<String, Object>> list() {
        AuthHelper.requireCatalogWriter();
        return service.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        AuthHelper.requireCatalogWriter();
        return service.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody ProductPayload req, HttpServletRequest http) {
        AuthHelper.requireCatalogWriter();
        return service.create(req, http);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody ProductPayload req,
                                      HttpServletRequest http) {
        AuthHelper.requireCatalogWriter();
        return service.update(id, req, http);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, HttpServletRequest http) {
        AuthHelper.requireCatalogWriter();
        service.delete(id, http);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/image")
    public Map<String, Object> uploadImage(@PathVariable String id,
                                           @RequestParam("file") MultipartFile file,
                                           HttpServletRequest http) {
        AuthHelper.requireCatalogWriter();
        return service.uploadImage(id, file, http);
    }
}
