package com.saga.orchestrator.dto;

import lombok.Data;

@Data
public class SagaRequest {
  private String userId;
  private String productId;
  private Integer quantity;
  private Long amount;
  private String simulateFailAt;
}
