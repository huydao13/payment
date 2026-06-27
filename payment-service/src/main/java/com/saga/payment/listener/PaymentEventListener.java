package com.saga.payment.listener;

import com.saga.payment.client.ProviderClient;
import com.saga.payment.dto.ProviderChargeResponse;
import com.saga.payment.entity.IdempotencyKey;
import com.saga.payment.entity.Payment;
import com.saga.payment.event.PaymentInitiatedEvent;
import com.saga.payment.repository.IdempotencyKeyRepository;
import com.saga.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

  private final ProviderClient providerClient;
  private final PaymentRepository paymentRepository;
  private final IdempotencyKeyRepository idempotencyKeyRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onPaymentInitiated(PaymentInitiatedEvent event) {
    log.info("[PaymentEventListener] Transaction đã commit, giờ gọi provider sagaId={}", event.getSagaId());

    try {
      ProviderChargeResponse providerRes = providerClient.charge(event.getSagaId(), event.getAmount());

      Payment payment = paymentRepository.findById(event.getPaymentId()).orElse(null);
      if (payment != null) {
        payment.setProviderTransactionId(providerRes.getTransactionId());
        paymentRepository.save(payment);
      }
      log.info("[PaymentEventListener] Đã forward sang provider, transactionId={}", providerRes.getTransactionId());

    } catch (Exception e) {
      log.error("[PaymentEventListener] Gọi provider thất bại: {}", e.getMessage());

      Payment payment = paymentRepository.findById(event.getPaymentId()).orElse(null);
      if (payment != null) {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        paymentRepository.save(payment);
      }

      IdempotencyKey key = idempotencyKeyRepository.findById(event.getSagaId()).orElse(null);
      if (key != null) {
        key.setStatus(IdempotencyKey.KeyStatus.FAILED);
        key.setErrorMessage("Provider không phản hồi: " + e.getMessage());
        idempotencyKeyRepository.save(key);
      }
      // Lưu ý: request charge() gốc đã trả "PENDING" về Orchestrator
      // từ trước. Nếu provider lỗi ở đây, Orchestrator không biết
      // ngay — phải đợi SagaRecoveryJob phát hiện saga "stuck" ở
      // PAYMENT_PENDING (mặc định 2 phút) rồi mới retry/fail.
    }
  }

}
