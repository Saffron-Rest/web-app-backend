package com.saffron.storefront.repository;

import com.saffron.storefront.domain.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /** Lets the admin/ops side quickly pull the day's reservations. */
    List<Reservation> findByDateOrderByTimeAsc(LocalDate date);

    long countByStatus(Reservation.Status status);

    long countByDateGreaterThanEqual(LocalDate from);

    // See CustomerOrderRepository.searchOrders for why :q is "" instead of NULL.
    @Query("""
        SELECT r FROM Reservation r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:from IS NULL OR r.date >= :from)
          AND (:to   IS NULL OR r.date <= :to)
          AND (:q = '' OR LOWER(r.name)  LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(r.email) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR r.phone        LIKE CONCAT('%', :q, '%'))
        ORDER BY r.date DESC, r.time ASC
    """)
    Page<Reservation> search(@Param("status") Reservation.Status status,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("q") String query,
                             Pageable pageable);
}
