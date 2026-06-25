package com.saga.payment.dto;

import lombok.Data;

@Data
public class WebhookPayload {
  private String transactionId;
  private String sagaId;
  private String status; // SUCCESS | FAILED
  private String message;
}
