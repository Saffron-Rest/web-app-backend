package com.saffron.storefront.service;

import com.saffron.storefront.domain.Product;
import com.saffron.storefront.domain.ProductCategory;
import com.saffron.storefront.repository.ProductRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminCatalogService {

    private final ProductRepository products;
    private final ImageStorageService images;
    private final StorefrontAuditService audit;

    public AdminCatalogService(ProductRepository products, ImageStorageService images, StorefrontAuditService audit) {
        this.products = products;
        this.images = images;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        return products.findAllByOrderByCategoryAscSortOrderAscNamePlAsc().stream()
                .map(CatalogService::toMap).map(m -> withActive(m, true)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        Map<String, Object> m = new java.util.LinkedHashMap<>(CatalogService.toMap(p));
        m.put("active", p.isActive());
        m.put("sortOrder", p.getSortOrder());
        return m;
    }

    public record ProductPayload(
            String slug, ProductCategory category,
            String namePl, String nameEn, String nameAz,
            String descriptionPl, String descriptionEn, String descriptionAz,
            String imageUrl,
            BigDecimal price, Integer weightGrams, Integer prepMinutes,
            Boolean availableInstant, Boolean availableCourier,
            Boolean active, Integer sortOrder
    ) {}

    @Transactional
    public Map<String, Object> create(ProductPayload req, HttpServletRequest http) {
        if (req.namePl() == null || req.namePl().isBlank()) throw new BadRequestException("namePl required");
        if (req.category() == null) throw new BadRequestException("category required");
        if (req.price() == null || req.price().signum() < 0) throw new BadRequestException("price required");

        Product p = new Product();
        p.setSlug(uniqueSlug(req.slug(), req.namePl(), null));
        p.setCategory(req.category());
        p.setNamePl(req.namePl().trim());
        p.setNameEn(trimToNull(req.nameEn()));
        p.setNameAz(trimToNull(req.nameAz()));
        p.setDescriptionPl(trimToNull(req.descriptionPl()));
        p.setDescriptionEn(trimToNull(req.descriptionEn()));
        p.setDescriptionAz(trimToNull(req.descriptionAz()));
        p.setImageUrl(trimToNull(req.imageUrl()));
        p.setPrice(req.price());
        if (req.weightGrams() != null) p.setWeightGrams(Math.max(1, req.weightGrams()));
        if (req.prepMinutes() != null) p.setPrepMinutes(Math.max(0, req.prepMinutes()));
        if (req.availableInstant() != null) p.setAvailableInstant(req.availableInstant());
        if (req.availableCourier() != null) p.setAvailableCourier(req.availableCourier());
        if (req.active() != null) p.setActive(req.active());
        if (req.sortOrder() != null) p.setSortOrder(req.sortOrder());
        Product saved = products.save(p);
        audit.record("PRODUCT_CREATE", "Product", saved.getId(),
                "slug=" + saved.getSlug() + ",name=" + saved.getNamePl(), http);
        return get(saved.getId());
    }

    @Transactional
    public Map<String, Object> update(String id, ProductPayload req, HttpServletRequest http) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        if (req.slug() != null && !req.slug().isBlank() && !req.slug().equals(p.getSlug())) {
            p.setSlug(uniqueSlug(req.slug(), p.getNamePl(), p.getId()));
        }
        if (req.category() != null) p.setCategory(req.category());
        if (req.namePl() != null && !req.namePl().isBlank()) p.setNamePl(req.namePl().trim());
        if (req.nameEn() != null) p.setNameEn(trimToNull(req.nameEn()));
        if (req.nameAz() != null) p.setNameAz(trimToNull(req.nameAz()));
        if (req.descriptionPl() != null) p.setDescriptionPl(trimToNull(req.descriptionPl()));
        if (req.descriptionEn() != null) p.setDescriptionEn(trimToNull(req.descriptionEn()));
        if (req.descriptionAz() != null) p.setDescriptionAz(trimToNull(req.descriptionAz()));
        if (req.imageUrl() != null) p.setImageUrl(trimToNull(req.imageUrl()));
        if (req.price() != null) p.setPrice(req.price());
        if (req.weightGrams() != null) p.setWeightGrams(Math.max(1, req.weightGrams()));
        if (req.prepMinutes() != null) p.setPrepMinutes(Math.max(0, req.prepMinutes()));
        if (req.availableInstant() != null) p.setAvailableInstant(req.availableInstant());
        if (req.availableCourier() != null) p.setAvailableCourier(req.availableCourier());
        if (req.active() != null) p.setActive(req.active());
        if (req.sortOrder() != null) p.setSortOrder(req.sortOrder());
        products.save(p);
        audit.record("PRODUCT_UPDATE", "Product", p.getId(), null, http);
        return get(p.getId());
    }

    @Transactional
    public void delete(String id, HttpServletRequest http) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        // Soft delete — preserve order line history.
        p.setActive(false);
        products.save(p);
        audit.record("PRODUCT_DELETE", "Product", p.getId(), "soft", http);
    }

    @Transactional
    public Map<String, Object> uploadImage(String id, MultipartFile file, HttpServletRequest http) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        String url = images.save(file);
        p.setImageUrl(url);
        products.save(p);
        audit.record("PRODUCT_IMAGE", "Product", p.getId(), url, http);
        return Map.of("imageUrl", url);
    }

    private String uniqueSlug(String desired, String fallbackName, String currentId) {
        String base = (desired == null || desired.isBlank()) ? slugify(fallbackName) : slugify(desired);
        if (base.isBlank()) base = "product";
        String candidate = base;
        int n = 2;
        while (true) {
            boolean clash = currentId == null
                    ? products.existsBySlug(candidate)
                    : products.existsBySlugAndIdNot(candidate, currentId);
            if (!clash) return candidate;
            candidate = base + "-" + n++;
            if (n > 1000) throw new BadRequestException("Cannot generate a unique slug");
        }
    }

    private static String slugify(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
    }

    private static Map<String, Object> withActive(Map<String, Object> m, boolean ignored) {
        return m;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
