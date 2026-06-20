package com.saga.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request object Orchestrator gửi sang Order Service
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderRequest {
    private String sagaId;          // Dùng làm idempotency key
    private String userId;
    private String productId;
    private Integer quantity;
    private Long amount;            // Tổng tiền
    private boolean simulateFail;   // Để test scenario fail
}
