package com.saga.orchestrator.controller;

import com.saga.orchestrator.entity.SagaState;
import com.saga.orchestrator.repository.SagaStateRepository;
import com.saga.orchestrator.service.SagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
@Tag(name = "SAGA Orchestrator", description = "Điều phối SAGA — Port 8080")
public class SagaController {

    private final SagaOrchestrator sagaOrchestrator;
    private final SagaStateRepository sagaStateRepository;

    /**
     * API chính để chạy SAGA
     * simulateFailAt: "ORDER" | "PAYMENT" | "INVENTORY" | null
     */
    @PostMapping("/start")
    @Operation(
        summary = "Bắt đầu SAGA đặt hàng",
        description = """
            Chạy SAGA với các scenario:
            - simulateFailAt = null → Happy path (thành công)
            - simulateFailAt = "ORDER" → Fail ở bước 1, không cần compensate
            - simulateFailAt = "PAYMENT" → Fail ở bước 2, compensate: cancelOrder
            - simulateFailAt = "INVENTORY" → Fail ở bước 3, compensate: refund → cancelOrder
            """
    )
    public ResponseEntity<SagaState> startSaga(@RequestBody SagaRequest request) {
        SagaState result = sagaOrchestrator.startSaga(
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                request.getAmount(),
                request.getSimulateFailAt()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Lấy trạng thái SAGA")
    public ResponseEntity<SagaState> getSaga(
            @Parameter(description = "SAGA ID") @PathVariable String sagaId) {
        return ResponseEntity.ok(sagaOrchestrator.getSaga(sagaId));
    }

    @GetMapping("/all")
    @Operation(summary = "Danh sách tất cả SAGA — dùng để monitor")
    public ResponseEntity<List<SagaState>> getAllSagas() {
        return ResponseEntity.ok(sagaStateRepository.findAll());
    }

    @DeleteMapping("/reset")
    @Operation(summary = "Xoá tất cả SAGA — dùng để test lại từ đầu")
    public ResponseEntity<String> reset() {
        sagaStateRepository.deleteAll();
        return ResponseEntity.ok("Reset thành công");
    }

    @Data
    public static class SagaRequest {
        private String userId = "user-001";
        private String productId = "product-A";
        private Integer quantity = 1;
        private Long amount = 300_000L;
        private String simulateFailAt; // null | ORDER | PAYMENT | INVENTORY
    }
}
