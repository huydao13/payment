package com.saga.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservations")
@Data
@NoArgsConstructor
public class InventoryReservation {

    @Id
    private String sagaId;        // Idempotency key

    private String productId;
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime createdAt;

    public enum ReservationStatus {
        RESERVED,   // Đã giữ hàng
        RELEASED    // Đã hoàn lại kho (compensation)
    }

    public InventoryReservation(String sagaId, String productId, Integer quantity) {
        this.sagaId = sagaId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = LocalDateTime.now();
    }
}
