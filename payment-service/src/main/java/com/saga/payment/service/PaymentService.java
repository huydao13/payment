package com.saga.payment.service;

import com.saga.payment.client.OrchestratorClient;
import com.saga.payment.client.ProviderClient;
import com.saga.payment.dto.OrderRequest;
import com.saga.payment.dto.ProviderChargeResponse;
import com.saga.payment.dto.ServiceResponse;
import com.saga.payment.dto.WebhookPayload;
import com.saga.payment.entity.IdempotencyKey;
import com.saga.payment.entity.Payment;
import com.saga.payment.repository.IdempotencyKeyRepository;
import com.saga.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrchestratorClient orchestratorClient;
    private final ProviderClient providerClient;


    /**
     * Charge tiền — GIỜ GỌI SANG mock-payment-provider, KHÔNG tự xử lý
     * nội bộ nữa.
     *
     * Khác biệt quan trọng so với code cũ: method này trả PENDING ngay,
     * KHÔNG biết kết quả thật (SUCCESS/FAILED) trong cùng request. Kết
     * quả thật chỉ về sau qua handleWebhook() — provider tự gọi ngược
     * lại endpoint webhook sau khi xử lý xong (giả lập độ trễ thật của
     * 1 payment gateway).
     *
     * Orchestrator nhận PENDING sẽ lưu SagaStep.PAYMENT_PENDING và dừng
     * — không tiến tới Inventory. Saga chỉ tiến lên PAYMENT_DONE khi
     * webhook báo SUCCESS gọi resumeFromWebhook() (xem SagaController).
     */
    @Transactional
    public ServiceResponse charge(OrderRequest request) {
        String idempotencyKey = request.getSagaId(); // sagaId = idempotency key
        log.info("[Payment Service] charge() sagaId={}, amount={}", idempotencyKey, request.getAmount());

        // ── Bước 1: Check idempotency key ───────────────────────
        IdempotencyKey existingKey = idempotencyKeyRepository.findById(idempotencyKey).orElse(null);
        if (existingKey != null) {
            switch (existingKey.getStatus()) {
                case PENDING -> {
                    log.warn("[Payment Service] Key PENDING — đã gọi provider rồi, chờ webhook");
                    return ServiceResponse.ok("Đang chờ provider xử lý (idempotent)",
                        existingKey.getPaymentId());
                }
                case SUCCESS -> {
                    log.info("[Payment Service] Key SUCCESS — đã charge rồi, trả về kết quả cũ");
                    return ServiceResponse.ok("Đã charge (idempotent)", existingKey.getPaymentId());
                }
                case FAILED -> log.info("[Payment Service] Key FAILED — thử lại");
            }
        }

        IdempotencyKey newKey = existingKey != null ? existingKey : new IdempotencyKey(idempotencyKey);
        newKey.setStatus(IdempotencyKey.KeyStatus.PENDING);
        idempotencyKeyRepository.save(newKey);

        // ── Simulate failure cũ — vẫn giữ để test nhanh không cần provider ──
        if (request.isSimulateFail()) {
            newKey.setStatus(IdempotencyKey.KeyStatus.FAILED);
            newKey.setErrorMessage("Simulated payment failure");
            idempotencyKeyRepository.save(newKey);
            return ServiceResponse.fail("Payment Service: Simulated failure");
        }
        String paymentId = UUID.randomUUID().toString();

        // ── Lưu Payment ở trạng thái PENDING — CHƯA trừ tiền thật ──
        Payment payment = new Payment(paymentId, idempotencyKey, request.getUserId(),
            request.getAmount(), null);
        paymentRepository.save(payment);

        newKey.setPaymentId(paymentId);
        idempotencyKeyRepository.save(newKey);

        // ── Gọi sang provider ────────────────────────────────────
        ProviderChargeResponse providerRes;
        try {
            providerRes = providerClient.charge(idempotencyKey, request.getAmount());
        } catch (Exception e) {
            // Provider không phản hồi được (network down, timeout...) —
            // đây KHÁC với provider trả FAILED nghiệp vụ (insufficient
            // funds...). Coi như fail ngay, không tạo Payment PENDING
            // treo vô thời hạn.
            log.error("[Payment Service] Gọi provider thất bại: {}", e.getMessage());
            newKey.setStatus(IdempotencyKey.KeyStatus.FAILED);
            newKey.setErrorMessage("Provider không phản hồi: " + e.getMessage());
            idempotencyKeyRepository.save(newKey);
            return ServiceResponse.fail("Payment provider không phản hồi");
        }
        payment.setProviderTransactionId(providerRes.getTransactionId());
        paymentRepository.save(payment);
        log.info("[Payment Service] Đã forward sang provider, paymentId={}, transactionId={} — chờ webhook",
            paymentId, providerRes.getTransactionId());

        // Trả "PENDING" — Orchestrator dựa vào đây để chuyển sang
        // SagaStep.PAYMENT_PENDING, không phải PAYMENT_DONE.
        return ServiceResponse.ok("PENDING", paymentId);
    }

    /**
     * Nhận webhook từ mock-payment-provider — đây là entry point MỚI,
     * không tồn tại trong code cũ. Provider tự gọi vào đây sau khi xử
     * lý xong (vài giây sau request charge() gốc).
     */
    @Transactional
    public ServiceResponse handleWebhook(WebhookPayload payload) {
        log.info("[Payment Service] Nhận webhook transactionId={}, sagaId={}, status={}",
            payload.getTransactionId(), payload.getSagaId(), payload.getStatus());

        Payment payment = paymentRepository.findBySagaId(payload.getSagaId()).orElse(null);
        if (payment == null) {
            log.error("[Payment Service] Webhook cho sagaId không tồn tại: {}", payload.getSagaId());
            return ServiceResponse.fail("Không tìm thấy payment cho sagaId: " + payload.getSagaId());
        }

        // Idempotency cho webhook — provider có thể gửi trùng (network
        // retry phía provider), không xử lý lại nếu đã có kết quả final.
        if (payment.getStatus() == Payment.PaymentStatus.CHARGED
            || payment.getStatus() == Payment.PaymentStatus.FAILED) {
            log.info("[Payment Service] Webhook trùng, đã xử lý trước đó — bỏ qua");
            return ServiceResponse.ok("Đã xử lý (idempotent)", payment.getPaymentId());
        }

        IdempotencyKey key = idempotencyKeyRepository.findById(payload.getSagaId()).orElse(null);
        boolean success = "SUCCESS".equalsIgnoreCase(payload.getStatus());

        if (success) {
            payment.setStatus(Payment.PaymentStatus.CHARGED);
            if (key != null) key.setStatus(IdempotencyKey.KeyStatus.SUCCESS);
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            if (key != null) {
                key.setStatus(IdempotencyKey.KeyStatus.FAILED);
                key.setErrorMessage(payload.getMessage());
            }
        }
        payment.setUpdatedAt(java.time.LocalDateTime.now());
        paymentRepository.save(payment);
        if (key != null) idempotencyKeyRepository.save(key);

        log.info("[Payment Service] Cập nhật payment {} → {}", payment.getPaymentId(), payment.getStatus());
        // ── Báo Orchestrator để resume saga ──────────────────────────
        orchestratorClient.notifyPaymentResult(payload.getSagaId(), success, payload.getMessage());
        return ServiceResponse.ok("Webhook xử lý thành công", payment.getPaymentId());
    }

    /**
     * Compensation: Hoàn tiền
     * Được gọi khi Inventory thất bại
     */
    @Transactional
    public ServiceResponse refund(String sagaId) {
        log.info("[Payment Service] refund() sagaId={}", sagaId);

        return paymentRepository.findBySagaId(sagaId).map(payment -> {
            if (payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
                return ServiceResponse.ok("Đã hoàn tiền rồi (idempotent)", payment.getPaymentId());
            }
            if (payment.getStatus() != Payment.PaymentStatus.CHARGED) {
                return ServiceResponse.fail("Không thể hoàn tiền — payment chưa ở trạng thái CHARGED: " + payment.getStatus());
            }
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("[Payment Service] Đã hoàn {}đ, balance mới={}", payment.getAmount(), sagaId);
            return ServiceResponse.ok("Hoàn tiền thành công", payment.getPaymentId());
        }).orElse(ServiceResponse.fail("Không tìm thấy payment để refund"));
    }
}
