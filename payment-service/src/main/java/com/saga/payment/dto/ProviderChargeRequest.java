package com.saga.payment.dto;

import lombok.Data;

@Data
public class ProviderChargeRequest {
  private String sagaId;
  private Long amount;
  private String forceOutcome;
}
