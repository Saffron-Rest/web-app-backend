package com.saffron.storefront.service;

import com.saffron.storefront.domain.Product;
import com.saffron.storefront.domain.ProductCategory;
import com.saffron.storefront.repository.ProductRepository;
import com.saffron.storefront.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** All active products grouped by category, ready for the storefront catalog page. */
    @Transactional(readOnly = true)
    public Map<String, Object> publicCatalog(String mode) {
        List<Product> products;
        if ("instant".equalsIgnoreCase(mode)) {
            products = productRepository.findByActiveTrueAndAvailableInstantTrueOrderByCategoryAscSortOrderAsc();
        } else if ("courier".equalsIgnoreCase(mode)) {
            products = productRepository.findByActiveTrueAndAvailableCourierTrueOrderByCategoryAscSortOrderAsc();
        } else {
            products = productRepository.findByActiveTrueOrderByCategoryAscSortOrderAscNamePlAsc();
        }

        Map<ProductCategory, List<Map<String, Object>>> grouped = new java.util.EnumMap<>(ProductCategory.class);
        for (Product p : products) {
            grouped.computeIfAbsent(p.getCategory(), k -> new java.util.ArrayList<>()).add(toMap(p));
        }

        List<Map<String, Object>> categories = new java.util.ArrayList<>();
        for (Map.Entry<ProductCategory, List<Map<String, Object>>> e : grouped.entrySet()) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("key", e.getKey().name());
            cat.put("items", e.getValue());
            categories.add(cat);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode == null ? "all" : mode.toLowerCase());
        body.put("categories", categories);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicProduct(String slug) {
        Product p = productRepository.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product not found: " + slug));
        return toMap(p);
    }

    public static Map<String, Object> toMap(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("slug", p.getSlug());
        m.put("category", p.getCategory().name());
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("pl", p.getNamePl());
        if (p.getNameEn() != null) name.put("en", p.getNameEn());
        if (p.getNameAz() != null) name.put("az", p.getNameAz());
        m.put("name", name);
        Map<String, Object> desc = new LinkedHashMap<>();
        if (p.getDescriptionPl() != null) desc.put("pl", p.getDescriptionPl());
        if (p.getDescriptionEn() != null) desc.put("en", p.getDescriptionEn());
        if (p.getDescriptionAz() != null) desc.put("az", p.getDescriptionAz());
        m.put("description", desc);
        m.put("imageUrl", p.getImageUrl());
        m.put("price", p.getPrice());
        m.put("weightGrams", p.getWeightGrams());
        m.put("prepMinutes", p.getPrepMinutes());
        m.put("availableInstant", p.isAvailableInstant());
        m.put("availableCourier", p.isAvailableCourier());
        return m;
    }
}
