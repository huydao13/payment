package com.saga.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

  private String sagaId;
  private String userId;
  private String productId;
  private Integer quantity;
  private Long amount;
  private boolean simulateFail;

}
