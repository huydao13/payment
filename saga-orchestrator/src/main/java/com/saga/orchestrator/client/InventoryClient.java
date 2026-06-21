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
public class InventoryClient {

    private final RestTemplate restTemplate;
    @Value("${services.inventory.url}/api/inventory")
    private String baseUrl;

    public ServiceResponse reserveStock(SagaState saga, boolean simulateFail) {
        log.info("[InventoryClient] POST /reserve sagaId={}", saga.getSagaId());
        OrderRequest request = OrderRequest.builder()
                .sagaId(saga.getSagaId())
                .productId(saga.getProductId())
                .quantity(saga.getQuantity())
                .simulateFail(simulateFail)
                .build();
        try {
            return restTemplate.postForObject(baseUrl + "/reserve", request, ServiceResponse.class);
        } catch (Exception e) {
            return ServiceResponse.fail("Inventory Service không phản hồi: " + e.getMessage());
        }
    }

    public ServiceResponse releaseStock(String sagaId) {
        log.warn("[InventoryClient] POST /release/{}", sagaId);
        try {
            return restTemplate.postForObject(baseUrl + "/release/" + sagaId, null, ServiceResponse.class);
        } catch (Exception e) {
            return ServiceResponse.fail("Release stock fail: " + e.getMessage());
        }
    }
}
