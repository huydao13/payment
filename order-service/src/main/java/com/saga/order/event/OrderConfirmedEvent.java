package com.saga.order.event;

import lombok.Getter;

@Getter
public class OrderConfirmedEvent {
  private final String orderId;
  private final String sagaId;
  private final String userId;

  public OrderConfirmedEvent(String orderId, String sagaId, String userId) {
    this.orderId = orderId;
    this.sagaId = sagaId;
    this.userId = userId;
  }
}
