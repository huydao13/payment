package com.saga.orchestrator.entity;

import com.saga.orchestrator.enums.SagaStatus;
import com.saga.orchestrator.enums.SagaStep;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * SagaState — lưu toàn bộ trạng thái của SAGA vào DB
 *
 * Tại sao quan trọng?
 * → Server crash bất kỳ lúc nào → restart → đọc bảng này → resume đúng chỗ
 * → Scheduled job quét bảng này để tìm SAGA bị stuck
 *
 * Rule: Lưu DB SAU MỖI BƯỚC — không được bỏ qua!
 */
@Entity
@Table(name = "saga_states")
@Data
@NoArgsConstructor
public class SagaState {

    @Id
    private String sagaId;

    // Request data
    private String userId;
    private String productId;
    private Integer quantity;
    private Long amount;

    // State machine
    @Enumerated(EnumType.STRING)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    // Reference IDs từ các service
    private String orderId;
    private String paymentId;

    // Retry tracking
    private int retryCount;
    private String errorMessage;

    // Scenario flags để test
    private String simulateFailAt; // "ORDER" | "PAYMENT" | "INVENTORY"

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt; // Scheduled job dùng để detect stuck

    public SagaState(String sagaId, String userId, String productId,
                     Integer quantity, Long amount, String simulateFailAt) {
        this.sagaId = sagaId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.simulateFailAt = simulateFailAt;
        this.currentStep = SagaStep.PENDING;
        this.status = SagaStatus.RUNNING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStep(SagaStep step) {
        this.currentStep = step;
        this.updatedAt = LocalDateTime.now();
    }
}
