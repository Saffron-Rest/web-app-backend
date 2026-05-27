package com.saffron.storefront.repository;

import com.saffron.storefront.domain.Product;
import com.saffron.storefront.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, String id);

    boolean existsBySlug(String slug);

    List<Product> findAllByOrderByCategoryAscSortOrderAscNamePlAsc();

    List<Product> findByActiveTrueOrderByCategoryAscSortOrderAscNamePlAsc();

    List<Product> findByActiveTrueAndAvailableInstantTrueOrderByCategoryAscSortOrderAsc();

    List<Product> findByActiveTrueAndAvailableCourierTrueOrderByCategoryAscSortOrderAsc();

    List<Product> findByActiveTrueAndCategoryOrderBySortOrderAscNamePlAsc(ProductCategory category);
}
