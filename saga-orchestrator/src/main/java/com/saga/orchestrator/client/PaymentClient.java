package com.saga.orchestrator.client;

import com.saga.orchestrator.dto.OrderRequest;
import com.saga.orchestrator.dto.ServiceResponse;
import com.saga.orchestrator.entity.SagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment.url}/api/payments")
    private String baseUrl;

    public ServiceResponse charge(SagaState saga, boolean simulateFail) {
        log.info("[PaymentClient] POST /charge sagaId={}, amount={}", saga.getSagaId(), saga.getAmount());
        OrderRequest request = OrderRequest.builder()
                .sagaId(saga.getSagaId())
                .userId(saga.getUserId())
                .amount(saga.getAmount())
                .simulateFail(simulateFail)
                .build();
        try {
            return restTemplate.postForObject(baseUrl + "/charge", request, ServiceResponse.class);
        } catch (Exception e) {
            log.error("[PaymentClient] charge fail: {}", e.getMessage());
            return ServiceResponse.fail("Payment Service không phản hồi: " + e.getMessage());
        }
    }

    public ServiceResponse refund(String sagaId) {
        log.warn("[PaymentClient] POST /refund/{}", sagaId);
        try {
            return restTemplate.postForObject(baseUrl + "/refund/" + sagaId, null, ServiceResponse.class);
        } catch (Exception e) {
            return ServiceResponse.fail("Refund fail: " + e.getMessage());
        }
    }

    /**
     * Tra cứu trạng thái payment thật, dùng cho SagaRecoveryJob hỏi
     * lại khi nghi ngờ webhook đã bị lỡ (saga stuck ở PAYMENT_PENDING
     * quá lâu, nhưng payment thực ra có thể đã CHARGED/FAILED từ lâu).
     * Đây là GET, không có side-effect, an toàn để gọi nhiều lần.
     */
    public ServiceResponse getStatus(String sagaId) {
        log.info("[PaymentClient] GET /status/{}", sagaId);
        try {
            return restTemplate.getForObject(baseUrl + "/status/" + sagaId, ServiceResponse.class);
        } catch (Exception e) {
            log.error("[PaymentClient] getStatus fail: {}", e.getMessage());
            return ServiceResponse.fail("Không hỏi được trạng thái payment: " + e.getMessage());
        }
    }
}
