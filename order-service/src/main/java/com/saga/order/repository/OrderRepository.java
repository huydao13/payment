package com.saga.order.repository;

import com.saga.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findBySagaId(String sagaId);
    boolean existsBySagaId(String sagaId);
}
