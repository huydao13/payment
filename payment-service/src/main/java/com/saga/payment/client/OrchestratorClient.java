package com.saga.payment.client;

import com.saga.payment.dto.PaymentWebhookSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class OrchestratorClient {
  private final RestTemplate restTemplate;

  @Value("${saga-orchestrator.url}")
  private String orchestratorUrl;

  public OrchestratorClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public void notifyPaymentResult(String sagaId, boolean success, String errorMessage) {
    PaymentWebhookSignal signal = new PaymentWebhookSignal();
      signal.setSuccess(success);
      signal.setErrorMessage(errorMessage);

    String url = orchestratorUrl + "/api/saga/" + sagaId + "/payment-webhook";
    try {
      log.info("[OrchestratorClient] Báo kết quả payment cho sagaId={}, success={}", sagaId, success);
      restTemplate.postForObject(url, signal, String.class);
    } catch (Exception e) {
      // Orchestrator không phản hồi được — đây là tình huống cần
      // SagaRecoveryJob xử lý: saga sẽ kẹt ở PAYMENT_PENDING, job
      // định kỳ (2 phút) sẽ phát hiện qua updatedAt cũ. Đây là lưới
      // an toàn thứ 2, không phải lỗi nghiêm trọng cần retry ngay.
      log.error("[OrchestratorClient] Gọi Orchestrator thất bại: {}", e.getMessage());
    }
  }
}
