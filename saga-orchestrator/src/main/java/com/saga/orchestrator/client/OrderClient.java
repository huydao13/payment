package com.saga.orchestrator.client;

import com.saga.orchestrator.dto.OrderRequest;
import com.saga.orchestrator.dto.ServiceResponse;
import com.saga.orchestrator.entity.SagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP Client gọi Order Service (port 8081)
 * Mỗi call đều gửi kèm sagaId làm idempotency key
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderClient {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "http://localhost:8081/api/orders";

    public ServiceResponse createOrder(SagaState saga, boolean simulateFail) {
        log.info("[OrderClient] POST /create sagaId={}", saga.getSagaId());
        OrderRequest request = OrderRequest.builder()
                .sagaId(saga.getSagaId())
                .userId(saga.getUserId())
                .productId(saga.getProductId())
                .quantity(saga.getQuantity())
                .amount(saga.getAmount())
                .simulateFail(simulateFail)
                .build();
        try {
            return restTemplate.postForObject(BASE_URL + "/create", request, ServiceResponse.class);
        } catch (Exception e) {
            log.error("[OrderClient] createOrder fail: {}", e.getMessage());
            return ServiceResponse.fail("Order Service không phản hồi: " + e.getMessage());
        }
    }

    public ServiceResponse cancelOrder(String sagaId) {
        log.warn("[OrderClient] POST /cancel/{}", sagaId);
        try {
            return restTemplate.postForObject(BASE_URL + "/cancel/" + sagaId, null, ServiceResponse.class);
        } catch (Exception e) {
            return ServiceResponse.fail("Cancel order fail: " + e.getMessage());
        }
    }

    public ServiceResponse confirmOrder(String sagaId) {
        log.info("[OrderClient] POST /confirm/{}", sagaId);
        try {
            return restTemplate.postForObject(BASE_URL + "/confirm/" + sagaId, null, ServiceResponse.class);
        } catch (Exception e) {
            return ServiceResponse.fail("Confirm order fail: " + e.getMessage());
        }
    }
}
