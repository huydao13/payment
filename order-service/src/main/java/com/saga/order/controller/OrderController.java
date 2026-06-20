package com.saga.order.controller;

import com.saga.order.dto.OrderRequest;
import com.saga.order.dto.ServiceResponse;
import com.saga.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Service", description = "Quản lý đơn hàng — Port 8081")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "Tạo đơn hàng", description = "Được gọi bởi SAGA Orchestrator. sagaId dùng làm idempotency key.")
    public ResponseEntity<ServiceResponse> createOrder(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @PostMapping("/cancel/{sagaId}")
    @Operation(summary = "Huỷ đơn hàng", description = "Compensation step — được gọi khi SAGA fail")
    public ResponseEntity<ServiceResponse> cancelOrder(@PathVariable String sagaId) {
        return ResponseEntity.ok(orderService.cancelOrder(sagaId));
    }

    @PostMapping("/confirm/{sagaId}")
    @Operation(summary = "Xác nhận đơn hàng", description = "Được gọi khi toàn bộ SAGA thành công")
    public ResponseEntity<ServiceResponse> confirmOrder(@PathVariable String sagaId) {
        return ResponseEntity.ok(orderService.confirmOrder(sagaId));
    }
}
