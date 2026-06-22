package com.saga.inventory.controller;

import com.saga.inventory.dto.ProductResponse;
import com.saga.inventory.repository.InventoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Catalog", description = "Catalog sản phẩm cho UI — đọc từ bảng inventory")
public class ProductController {
  private final InventoryRepository inventoryRepository;

  @GetMapping
  @Operation(summary = "Lấy toàn bộ catalog sản phẩm")
  public ResponseEntity<List<ProductResponse>> getAllProducts() {
    List<ProductResponse> products = inventoryRepository.findAll()
        .stream()
        .map(ProductResponse::from)
        .toList();
    return ResponseEntity.ok(products);
  }

  @GetMapping("/{productId}")
  @Operation(summary = "Lấy chi tiết 1 sản phẩm theo productId")
  public ResponseEntity<ProductResponse> getProduct(
      @Parameter(description = "Product ID, ví dụ product-A") @PathVariable String productId) {
    return inventoryRepository.findById(productId)
        .map(ProductResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }
}
