package com.saga.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {
  private boolean success;
  private String message;
  private String referenceId;
  private Object data;

  public static ServiceResponse ok(String message, String refId) {
    return ServiceResponse.builder()
        .success(true).message(message).referenceId(refId).build();
  }

  public static ServiceResponse fail(String message) {
    return ServiceResponse.builder()
        .success(false).message(message).build();
  }
}
