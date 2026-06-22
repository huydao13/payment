package com.saga.inventory.dto;

import com.saga.inventory.entity.Inventory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
  private String productId;
  private String productName;
  private Integer quantity;
  private Long price;
  private String imageUrl;
  private boolean inStock;

  public static ProductResponse from(Inventory inventory) {
    return new ProductResponse(
        inventory.getProductId(),
        inventory.getProductName(),
        inventory.getQuantity(),
        inventory.getPrice(),
        inventory.getImageUrl(),
        inventory.getQuantity() != null && inventory.getQuantity() > 0
    );
  }
}
