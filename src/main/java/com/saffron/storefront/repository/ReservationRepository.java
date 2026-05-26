package com.saffron.storefront.repository;

import com.saffron.storefront.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /** Lets the admin/ops side quickly pull the day's reservations. */
    List<Reservation> findByDateOrderByTimeAsc(LocalDate date);
}
