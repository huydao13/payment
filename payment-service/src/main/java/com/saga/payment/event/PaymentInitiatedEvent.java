package com.saga.payment.event;

import lombok.Data;

@Data
public class PaymentInitiatedEvent {
  private final String paymentId;
  private final String sagaId;
  private final Long amount;

  public PaymentInitiatedEvent(String paymentId, String sagaId, Long amount) {
    this.paymentId = paymentId;
    this.sagaId = sagaId;
    this.amount = amount;
  }
}
