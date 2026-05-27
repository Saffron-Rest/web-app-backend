package com.saffron.storefront.service;

import com.saffron.storefront.domain.OrderStatus;
import com.saffron.storefront.domain.Reservation;
import com.saffron.storefront.repository.CustomerOrderRepository;
import com.saffron.storefront.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {

    private final CustomerOrderRepository orders;
    private final ReservationRepository reservations;

    public AdminDashboardService(CustomerOrderRepository orders, ReservationRepository reservations) {
        this.orders = orders;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        var todayStart = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant();

        Map<String, Object> ordersBlock = new LinkedHashMap<>();
        ordersBlock.put("today", orders.countByCreatedAtAfter(todayStart));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), orders.countByStatus(s));
        ordersBlock.put("byStatus", byStatus);

        Map<String, Object> reservationsBlock = new LinkedHashMap<>();
        reservationsBlock.put("upcoming", reservations.countByDateGreaterThanEqual(LocalDate.now()));
        Map<String, Long> resStatus = new LinkedHashMap<>();
        for (Reservation.Status s : Reservation.Status.values()) resStatus.put(s.name(), reservations.countByStatus(s));
        reservationsBlock.put("byStatus", resStatus);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", ordersBlock);
        body.put("reservations", reservationsBlock);

        // Recent activity — last 10 orders + today's reservations
        List<Map<String, Object>> recentOrders = orders
                .searchOrders(null, null, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream().map(AdminOrderService::summary).toList();
        body.put("recentOrders", recentOrders);

        List<Map<String, Object>> todayRes = reservations
                .findByDateOrderByTimeAsc(LocalDate.now())
                .stream().map(AdminReservationService::toMap).toList();
        body.put("todayReservations", todayRes);

        return body;
    }
}
