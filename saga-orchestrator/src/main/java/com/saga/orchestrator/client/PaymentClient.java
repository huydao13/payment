package com.saga.orchestrator.client;

import com.saga.orchestrator.dto.OrderRequest;
import com.saga.orchestrator.dto.ServiceResponse;
import com.saga.orchestrator.entity.SagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentClient {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "http://localhost:8082/api/payments";

    public ServiceResponse charge(SagaState saga, boolean simulateFail) {
        log.info("[PaymentClient] POST /charge sagaId={}, amount={}", saga.getSagaId(), saga.getAmount());
        OrderRequest request = OrderRequest.builder()
                .sagaId(saga.getSagaId())
                .userId(saga.getUserId())
                .amount(saga.getAmount())
                .simulateFail(simulateFail)
                .build();
        try {
            return restTemplate.postForObject(BASE_URL + "/charge", request, ServiceResponse.class);
        } catch (Exception e) {
            log.error("[PaymentClient] charge fail: {}", e.getMessage());
            return ServiceResponse.fail("Payment Service không phản hồi: " + e.getMessage());
        }
    }

    public ServiceResponse refund(String sagaId) {
        log.warn("[PaymentClient] POST /refund/{}", sagaId);
        try {
            return restTemplate.postForObject(BASE_URL + "/refund/" + sagaId, null, ServiceResponse.class);
        } catch (Exception e) {
            return ServiceResponse.fail("Refund fail: " + e.getMessage());
        }
    }
}
