package com.saga.order.listener;

import com.saga.order.event.OrderConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class NotificationEventListener {
  /**
   * Giả lập gửi email xác nhận — đúng vị trí kiến trúc Outbox pattern
   * (chạy SAU KHI order CONFIRMED đã commit, không gọi service ngoài
   * trong transaction gốc) như đã áp dụng cho payment provider.
   *
   * Production thật: thay log.info() bằng gọi EmailService/SMS gateway.
   * Giữ nguyên @Async + AFTER_COMMIT để không lặp lại race condition
   * đã gặp với webhook payment.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    log.info("[Notification] Giả lập gửi email xác nhận đơn hàng → userId={}, orderId={}, sagaId={}",
        event.getUserId(), event.getOrderId(), event.getSagaId());
    // TODO: production — gọi EmailService.send(...) hoặc đẩy event
    // sang Notification Service riêng qua message queue
  }
}
