package com.saga.orchestrator.dto;

import lombok.Data;

@Data
public class PaymentWebhookSignal {
  private boolean success;
  private String errorMessage;
}
