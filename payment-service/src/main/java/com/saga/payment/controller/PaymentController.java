package com.saga.payment.controller;

import com.saga.payment.dto.OrderRequest;
import com.saga.payment.dto.ServiceResponse;
import com.saga.payment.dto.WebhookPayload;
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
@Tag(name = "Payment Service", description = "Quản lý thanh toán qua mock-payment-provider")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    @Operation(
        summary = "Charge tiền  — forward sang provider",
        description = "Trả PENDING ngay, kết quả thật về sau qua /webhook"
    )
    public ResponseEntity<ServiceResponse> charge(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(paymentService.charge(request));
    }

    @PostMapping("/refund/{sagaId}")
    @Operation(summary = "Hoàn tiền", description = "Compensation step — được gọi khi Inventory fail")
    public ResponseEntity<ServiceResponse> refund(@PathVariable String sagaId) {
        return ResponseEntity.ok(paymentService.refund(sagaId));
    }

    @PostMapping("/webhook")
    @Operation(
        summary = "Nhận webhook từ mock-payment-provider",
        description = "Provider tự gọi vào đây sau khi xử lý xong giao dịch (async)"
    )
    public ResponseEntity<ServiceResponse> webhook(@RequestBody WebhookPayload payload) {
        return ResponseEntity.ok(paymentService.handleWebhook(payload));
    }

    @GetMapping("/status/{sagaId}")
    @Operation(summary = "Tra cứu trạng thái payment hiện tại theo sagaId")
    public ResponseEntity<ServiceResponse> getStatus(@PathVariable String sagaId) {
        return ResponseEntity.ok(paymentService.getStatus(sagaId));
    }
}
