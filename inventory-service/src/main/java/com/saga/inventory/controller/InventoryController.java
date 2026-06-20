package com.saga.inventory.controller;


import com.saga.inventory.dto.OrderRequest;
import com.saga.inventory.dto.ServiceResponse;
import com.saga.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Service", description = "Quản lý kho với Optimistic Locking — Port 8083")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    @Operation(summary = "Giữ hàng", description = "Optimistic Locking + Idempotency Key")
    public ResponseEntity<ServiceResponse> reserveStock(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(inventoryService.reserveStock(request));
    }

    @PostMapping("/release/{sagaId}")
    @Operation(summary = "Hoàn kho — compensation step")
    public ResponseEntity<ServiceResponse> releaseStock(@PathVariable String sagaId) {
        return ResponseEntity.ok(inventoryService.releaseStock(sagaId));
    }
}
