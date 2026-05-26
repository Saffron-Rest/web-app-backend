package com.saffron.storefront.repository;

import com.saffron.storefront.domain.CustomerOrder;
import com.saffron.storefront.domain.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, String> {

    @EntityGraph(attributePaths = {"lines"})
    Optional<CustomerOrder> findByReference(String reference);

    @EntityGraph(attributePaths = {"lines"})
    Optional<CustomerOrder> findWithLinesById(String id);

    List<CustomerOrder> findByStatusIn(Collection<OrderStatus> statuses);
}
