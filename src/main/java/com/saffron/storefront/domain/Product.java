package com.saffron.storefront.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "ix_products_slug", columnList = "slug", unique = true),
        @Index(name = "ix_products_category_active", columnList = "category,active")
})
public class Product {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    /** URL-safe identifier, e.g. "lamb-plov". Unique. */
    @Column(nullable = false, length = 120)
    private String slug;

    /** Logical category (e.g. MAIN, STARTER, DESSERT, DRINK, MERCH). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductCategory category;

    /** Default-language name (Polish). */
    @Column(name = "name_pl", nullable = false, length = 200)
    private String namePl;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "name_az", length = 200)
    private String nameAz;

    @Column(name = "description_pl", columnDefinition = "TEXT")
    private String descriptionPl;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "description_az", columnDefinition = "TEXT")
    private String descriptionAz;

    /** Display image, served from /uploads or a CDN. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Price including VAT, in {@code app.currency}. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Weight in grams — used to bound carrier quotes (DHL parcel size). */
    @Column(name = "weight_grams", nullable = false)
    private int weightGrams = 500;

    /** Approximate prep time (minutes), surfaced to the customer at checkout. */
    @Column(name = "prep_minutes", nullable = false)
    private int prepMinutes = 20;

    /** Available for instant (Wolt/Glovo) delivery — usually true for hot food. */
    @Column(name = "available_instant", nullable = false)
    private boolean availableInstant = true;

    /** Available for worldwide courier shipping — usually only shelf-stable goods. */
    @Column(name = "available_courier", nullable = false)
    private boolean availableCourier = false;

    /** Soft-disable without deleting. */
    @Column(nullable = false)
    private boolean active = true;

    /** Display order within the category (ascending). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }
    public String getNamePl() { return namePl; }
    public void setNamePl(String namePl) { this.namePl = namePl; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameAz() { return nameAz; }
    public void setNameAz(String nameAz) { this.nameAz = nameAz; }
    public String getDescriptionPl() { return descriptionPl; }
    public void setDescriptionPl(String descriptionPl) { this.descriptionPl = descriptionPl; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    public String getDescriptionAz() { return descriptionAz; }
    public void setDescriptionAz(String descriptionAz) { this.descriptionAz = descriptionAz; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getWeightGrams() { return weightGrams; }
    public void setWeightGrams(int weightGrams) { this.weightGrams = weightGrams; }
    public int getPrepMinutes() { return prepMinutes; }
    public void setPrepMinutes(int prepMinutes) { this.prepMinutes = prepMinutes; }
    public boolean isAvailableInstant() { return availableInstant; }
    public void setAvailableInstant(boolean availableInstant) { this.availableInstant = availableInstant; }
    public boolean isAvailableCourier() { return availableCourier; }
    public void setAvailableCourier(boolean availableCourier) { this.availableCourier = availableCourier; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
