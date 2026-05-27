package com.saffron.storefront.repository;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, String> {

    @EntityGraph(attributePaths = {"lines"})
    Optional<CustomerOrder> findByReference(String reference);

    @EntityGraph(attributePaths = {"lines"})
    Optional<CustomerOrder> findWithLinesById(String id);

    List<CustomerOrder> findByStatusIn(Collection<OrderStatus> statuses);

    long countByStatus(OrderStatus status);

    long countByCreatedAtAfter(Instant after);

    // NOTE on the :q parameter — we pass an empty string ("") to mean "no filter"
    // rather than NULL. PostgreSQL's JDBC binding can't infer the type of a NULL
    // String inside `LOWER(CONCAT('%', :q, '%'))` and falls back to bytea, which
    // then fails with `function lower(bytea) does not exist`. With a non-null
    // string the parameter is bound as varchar and the planner is happy.
    @Query("""
        SELECT o FROM CustomerOrder o
        WHERE (:status IS NULL OR o.status = :status)
          AND (:q = '' OR LOWER(o.reference)    LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(o.contactEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(o.contactName)  LIKE LOWER(CONCAT('%', :q, '%'))
                       OR o.contactPhone        LIKE CONCAT('%', :q, '%'))
        ORDER BY o.createdAt DESC
    """)
    Page<CustomerOrder> searchOrders(@Param("status") OrderStatus status,
                                     @Param("q") String query,
                                     Pageable pageable);

    @Query("""
        SELECT new com.saffron.storefront.repository.CustomerSummary(
            LOWER(o.contactEmail),
            MAX(o.contactName),
            MAX(o.contactPhone),
            COUNT(o),
            MAX(o.createdAt),
            COALESCE(SUM(o.total), 0))
        FROM CustomerOrder o
        WHERE (:q = '' OR LOWER(o.contactEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(o.contactName)  LIKE LOWER(CONCAT('%', :q, '%'))
                       OR o.contactPhone        LIKE CONCAT('%', :q, '%'))
        GROUP BY LOWER(o.contactEmail)
        ORDER BY MAX(o.createdAt) DESC
    """)
    Page<CustomerSummary> findCustomers(@Param("q") String query, Pageable pageable);
}
