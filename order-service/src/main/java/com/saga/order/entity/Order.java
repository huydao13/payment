package com.saga.order.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    private String orderId;

    private String sagaId;
    private String userId;
    private String productId;
    private Integer quantity;
    private Long amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum OrderStatus {
        CREATED,
        CONFIRMED,
        CANCELLED
    }

    public Order(String orderId, String sagaId, String userId,
                 String productId, Integer quantity, Long amount) {
        this.orderId = orderId;
        this.sagaId = sagaId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
