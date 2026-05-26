package com.saffron.storefront.repository;

import com.saffron.storefront.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, String> {
    List<OrderEvent> findByOrderIdOrderByCreatedAtAsc(String orderId);
}
