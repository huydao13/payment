package com.saga.payment.client;

import com.saga.payment.dto.ProviderChargeRequest;
import com.saga.payment.dto.ProviderChargeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class ProviderClient {
  private final RestTemplate restTemplate;

  @Value("${mock-payment-provider.url}")
  private String providerUrl;

  public ProviderClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public ProviderChargeResponse charge(String sagaId, Long amount) {
    ProviderChargeRequest request = new ProviderChargeRequest();
    request.setSagaId(sagaId);
    request.setAmount(amount);

    log.info("[ProviderClient] Gọi sang provider charge sagaId={}, amount={}", sagaId, amount);
    return restTemplate.postForObject(
        providerUrl + "/api/provider/charge", request, ProviderChargeResponse.class);
  }
}
