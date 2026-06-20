package com.saga.payment.controller;

import com.saga.payment.dto.OrderRequest;
import com.saga.payment.dto.ServiceResponse;
import com.saga.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Service", description = "Quản lý thanh toán với Idempotency Key — Port 8082")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    @Operation(
        summary = "Charge tiền",
        description = "sagaId dùng làm idempotency key. Retry bao nhiêu lần cũng không charge 2 lần!"
    )
    public ResponseEntity<ServiceResponse> charge(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(paymentService.charge(request));
    }

    @PostMapping("/refund/{sagaId}")
    @Operation(summary = "Hoàn tiền", description = "Compensation step — được gọi khi Inventory fail")
    public ResponseEntity<ServiceResponse> refund(@PathVariable String sagaId) {
        return ResponseEntity.ok(paymentService.refund(sagaId));
    }

    @GetMapping("/balance")
    @Operation(summary = "Xem số dư tài khoản")
    public ResponseEntity<Map<String, Long>> getBalance() {
        return ResponseEntity.ok(Map.of("balance", paymentService.getBalance()));
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset số dư về 2,000,000đ — dùng để test")
    public ResponseEntity<String> reset() {
        PaymentService.resetBalance();
        return ResponseEntity.ok("Reset thành công");
    }
}
