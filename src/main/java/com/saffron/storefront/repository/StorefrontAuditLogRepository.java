package com.saffron.storefront.repository;

import com.saffron.storefront.domain.StorefrontAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorefrontAuditLogRepository extends JpaRepository<StorefrontAuditLog, String> {

    // See CustomerOrderRepository.searchOrders for why :q is "" instead of NULL.
    @Query("""
        SELECT a FROM StorefrontAuditLog a
        WHERE (:q = '' OR LOWER(a.userEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(a.action)    LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(a.entity)    LIKE LOWER(CONCAT('%', :q, '%'))
                       OR a.entityId         LIKE CONCAT('%', :q, '%'))
        ORDER BY a.createdAt DESC
    """)
    Page<StorefrontAuditLog> search(@Param("q") String query, Pageable pageable);
}
