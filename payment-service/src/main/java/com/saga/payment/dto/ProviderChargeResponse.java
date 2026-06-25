package com.saga.payment.dto;

import lombok.Data;

@Data
public class ProviderChargeResponse {

  private String sagaId;
  private Long amount;
  private String forceOutcome;
  private String transactionId;

}
