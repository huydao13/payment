package com.saga.payment.service;

import com.saga.payment.dto.OrderRequest;
import com.saga.payment.dto.ServiceResponse;
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

    // Giả lập số dư tài khoản (trong thực tế sẽ từ DB)
    private static long accountBalance = 2_000_000L;

    /**
     * Charge tiền với Idempotency Key
     *
     * Flow:
     * 1. Check key trong DB
     * 2. PENDING → đang xử lý → trả về "thử lại sau"
     * 3. SUCCESS → đã charge → trả về kết quả cũ (không charge lại)
     * 4. Chưa có → lưu PENDING → charge → lưu SUCCESS
     *
     * sagaId được dùng làm idempotency key
     * → Orchestrator retry bao nhiêu lần cũng không charge 2 lần!
     */
    @Transactional
    public ServiceResponse charge(OrderRequest request) {
        String idempotencyKey = request.getSagaId(); // sagaId = idempotency key
        log.info("[Payment Service] charge() sagaId={}, amount={}", idempotencyKey, request.getAmount());

        // ── Bước 1: Check idempotency key ───────────────────────
        IdempotencyKey existingKey = idempotencyKeyRepository.findById(idempotencyKey).orElse(null);

        if (existingKey != null) {
            switch (existingKey.getStatus()) {
                case PENDING:
                    // Đang xử lý dở — có thể do concurrent request
                    log.warn("[Payment Service] Key PENDING — đang xử lý, thử lại sau");
                    return ServiceResponse.fail("Payment đang xử lý, thử lại sau 5 giây");
                case SUCCESS:
                    // Đã charge rồi — đây là retry
                    log.info("[Payment Service] Key SUCCESS — đã charge rồi, trả về kết quả cũ");
                    return ServiceResponse.ok("Đã charge (idempotent)", existingKey.getPaymentId());
                case FAILED:
                    // Lần trước fail — thử lại
                    log.info("[Payment Service] Key FAILED — thử lại");
                    break;
            }
        }

        // ── Bước 2: Lưu PENDING ngay trước khi xử lý ───────────
        // Quan trọng! Lưu trước để tránh concurrent request cùng charge
        IdempotencyKey newKey = new IdempotencyKey(idempotencyKey);
        idempotencyKeyRepository.save(newKey);

        // ── Bước 3: Simulate failure ─────────────────────────────
        if (request.isSimulateFail()) {
            newKey.setStatus(IdempotencyKey.KeyStatus.FAILED);
            newKey.setErrorMessage("Simulated payment failure");
            idempotencyKeyRepository.save(newKey);
            log.warn("[Payment Service] Simulate fail!");
            return ServiceResponse.fail("Payment Service: Simulated failure");
        }

        // ── Bước 4: Kiểm tra số dư ───────────────────────────────
        if (accountBalance < request.getAmount()) {
            newKey.setStatus(IdempotencyKey.KeyStatus.FAILED);
            newKey.setErrorMessage("Insufficient funds");
            idempotencyKeyRepository.save(newKey);
            return ServiceResponse.fail("Số dư không đủ: " + accountBalance + "đ < " + request.getAmount() + "đ");
        }

        // ── Bước 5: Trừ tiền ─────────────────────────────────────
        accountBalance -= request.getAmount();
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment(paymentId, idempotencyKey, request.getUserId(), request.getAmount());
        paymentRepository.save(payment);

        // ── Bước 6: Cập nhật key → SUCCESS ───────────────────────
        newKey.setStatus(IdempotencyKey.KeyStatus.SUCCESS);
        newKey.setPaymentId(paymentId);
        idempotencyKeyRepository.save(newKey);

        log.info("[Payment Service] Charge thành công! paymentId={}, balance còn={}", paymentId, accountBalance);
        return ServiceResponse.ok("Charge thành công", paymentId);
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
            accountBalance += payment.getAmount();
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("[Payment Service] Đã hoàn {}đ, balance mới={}", payment.getAmount(), accountBalance);
            return ServiceResponse.ok("Hoàn tiền thành công", payment.getPaymentId());
        }).orElse(ServiceResponse.fail("Không tìm thấy payment để refund"));
    }

    public long getBalance() { return accountBalance; }

    public static void resetBalance() { accountBalance = 2_000_000L; }
}
