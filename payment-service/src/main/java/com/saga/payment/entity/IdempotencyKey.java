package com.saga.payment.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Idempotency Key Table
 *
 * Tại sao cần bảng này?
 * → Orchestrator có thể retry khi timeout
 * → Không có bảng này → charge 2 lần!
 *
 * Flow:
 * 1. Request đến → check key trong bảng này
 * 2. Chưa có → lưu PENDING → xử lý → lưu SUCCESS
 * 3. Đang PENDING → trả về "đang xử lý, thử lại sau"
 * 4. SUCCESS → trả về kết quả cũ, không charge lại
 */
@Entity
@Table(name = "idempotency_keys")
@Data
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    private String idempotencyKey;  // sagaId từ Orchestrator

    @Enumerated(EnumType.STRING)
    private KeyStatus status;

    private String paymentId;       // Kết quả để trả về khi retry
    private String errorMessage;    // Nếu fail
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum KeyStatus {
        PENDING,   // Đang xử lý
        SUCCESS,   // Đã xong thành công
        FAILED     // Đã xong thất bại
    }

    public IdempotencyKey(String key) {
        this.idempotencyKey = key;
        this.status = KeyStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
