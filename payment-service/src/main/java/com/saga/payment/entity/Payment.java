package com.saga.payment.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class Payment {

    @Id
    private String paymentId;

    private String sagaId;
    private String userId;
    private Long amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        CHARGED,   // Đã trừ tiền
        REFUNDED   // Đã hoàn tiền (compensation)
    }

    public Payment(String paymentId, String sagaId, String userId, Long amount) {
        this.paymentId = paymentId;
        this.sagaId = sagaId;
        this.userId = userId;
        this.amount = amount;
        this.status = PaymentStatus.CHARGED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
