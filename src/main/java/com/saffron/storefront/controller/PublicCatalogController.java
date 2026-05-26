package com.saffron.storefront.controller;

import com.saffron.storefront.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class PublicCatalogController {

    private final CatalogService catalogService;

    public PublicCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** mode = "instant" | "courier" | omitted (all active products). */
    @GetMapping
    public Map<String, Object> catalog(@RequestParam(value = "mode", required = false) String mode) {
        return catalogService.publicCatalog(mode);
    }

    @GetMapping("/{slug}")
    public Map<String, Object> product(@PathVariable String slug) {
        return catalogService.publicProduct(slug);
    }
}
