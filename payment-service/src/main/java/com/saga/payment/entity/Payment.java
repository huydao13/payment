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

    /**
     * transactionId từ mock-payment-provider — dùng để webhook xác định
     * đúng Payment nào cần update khi callback về (provider không biết
     * gì về sagaId/paymentId nội bộ của ta, chỉ biết transactionId nó tạo).
     */
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        PENDING,   // Đã gọi provider, đang chờ webhook báo kết quả
        CHARGED,   // Webhook báo SUCCESS — đã trừ tiền thật
        FAILED,    // Webhook báo FAILED — provider từ chối giao dịch
        REFUNDED   // Đã hoàn tiền (compensation)
    }

    /**
     * Constructor cho trạng thái PENDING — dùng ngay khi gọi provider,
     * CHƯA biết kết quả thật. Status CHARGED chỉ được set sau khi nhận
     * webhook SUCCESS (xem PaymentService.handleWebhook).
     */
    public Payment(String paymentId, String sagaId, String userId, Long amount, String providerTransactionId) {
        this.paymentId = paymentId;
        this.sagaId = sagaId;
        this.userId = userId;
        this.amount = amount;
        this.providerTransactionId = providerTransactionId;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
