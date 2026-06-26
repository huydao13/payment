package com.saga.orchestrator.service;

import com.saga.orchestrator.client.InventoryClient;
import com.saga.orchestrator.client.OrderClient;
import com.saga.orchestrator.client.PaymentClient;
import com.saga.orchestrator.dto.ServiceResponse;
import com.saga.orchestrator.entity.SagaState;
import com.saga.orchestrator.enums.SagaStatus;
import com.saga.orchestrator.enums.SagaStep;
import com.saga.orchestrator.repository.SagaStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * SAGA Orchestrator — điều phối toàn bộ flow
 *
 * State Machine:
 * PENDING → ORDER_CREATED → PAYMENT_DONE → INVENTORY_RESERVED → COMPLETED
 *
 * Compensation (ngược):
 * COMPENSATING_INVENTORY → COMPENSATING_PAYMENT → COMPENSATING_ORDER → CANCELLED
 *
 * Rule quan trọng:
 * → Lưu SagaState vào DB sau MỖI bước
 * → Server crash bất kỳ lúc → restart → scheduled job resume từ currentStep
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    // ── Khởi tạo SAGA ────────────────────────────────────────────

    /**
     * QUAN TRỌNG: KHÔNG dùng @Transactional ở đây!
     *
     * Nếu @Transactional bọc method này, Spring sẽ giữ 1 transaction
     * mở xuyên suốt TOÀN BỘ SAGA flow — bao gồm cả các HTTP call ra ngoài
     * (có thể mất vài giây). SagaState.save() bên trong executeStep()
     * sẽ KHÔNG thực sự commit cho đến khi method này return.
     *
     * Hậu quả: nếu server crash giữa chừng → transaction rollback toàn bộ
     * → SagaState KHÔNG được lưu → mất hết ý nghĩa "lưu sau mỗi bước"!
     *
     * Mỗi lời gọi sagaStateRepository.save() bên trong executeStep()
     * tự commit độc lập (Spring Data JPA mặc định @Transactional cho
     * từng method CRUD) — đây chính xác là hành vi ta cần.
     */
    public SagaState startSaga(String userId, String productId,
                                Integer quantity, Long amount, String simulateFailAt) {
        String sagaId = UUID.randomUUID().toString();
        log.info("[Orchestrator] ═══ Bắt đầu SAGA sagaId={} ═══", sagaId);

        // Lưu PENDING ngay lập tức — trước khi làm bất cứ gì
        // Nếu crash sau dòng này → scheduled job phát hiện và resume
        SagaState saga = new SagaState(sagaId, userId, productId, quantity, amount, simulateFailAt);
        sagaStateRepository.save(saga);
        log.info("[Orchestrator] SagaState PENDING lưu vào DB ✓");

        // Bắt đầu chạy bước đầu tiên
        executeStep(saga);
        return sagaStateRepository.findById(sagaId).orElse(saga);
    }

    // ── Execute next step ─────────────────────────────────────────

    /**
     * Đây là trái tim của State Machine
     * Mỗi case là 1 bước trong flow — gọi service → nếu thành công → lưu state mới → chạy tiếp
     */
    public void executeStep(SagaState saga) {
        log.info("[Orchestrator] executeStep: currentStep={}", saga.getCurrentStep());

        try {
            switch (saga.getCurrentStep()) {

                case PENDING -> {
                    // Bước 1: Tạo đơn hàng
                    boolean failOrder = "ORDER".equals(saga.getSimulateFailAt());
                    ServiceResponse res = orderClient.createOrder(saga, failOrder);

                    if (!res.isSuccess()) {
                        log.error("[Orchestrator] Order fail: {}", res.getMessage());
                        saga.setErrorMessage(res.getMessage());
                        startCompensation(saga); // Bước 1 fail → không có gì để compensate
                        return;
                    }
                    saga.setOrderId(res.getReferenceId());
                    saga.updateStep(SagaStep.ORDER_CREATED);
                    save(saga, "ORDER_CREATED ✓");
                    executeStep(saga); // Tiếp tục bước tiếp theo
                }

                case ORDER_CREATED -> {
                    // Gọi Payment — GIỜ KHÔNG biết kết quả thật ngay.
                    // Payment Service trả "PENDING" (forward sang provider),
                    // không phải kết quả final. Ta CHỈ chuyển sang
                    // PAYMENT_PENDING và DỪNG — không gọi executeStep tiếp.
                    // Saga sẽ "ngủ" ở đây cho tới khi webhook về, gọi
                    // resumeFromPaymentWebhook().
                    boolean failPayment = "PAYMENT".equals(saga.getSimulateFailAt());
                    ServiceResponse res = paymentClient.charge(saga, failPayment);

                    if (!res.isSuccess()) {
                        // Fail ngay tại bước gọi (network down, simulateFail,
                        // hoặc provider không phản hồi) — không phải fail
                        // nghiệp vụ qua webhook. Compensate luôn.
                        log.error("[Orchestrator] Payment fail: {}", res.getMessage());
                        saga.setErrorMessage(res.getMessage());
                        startCompensation(saga);
                        return;
                    }
                    saga.setPaymentId(res.getReferenceId());
                    saga.updateStep(SagaStep.PAYMENT_PENDING);
                    save(saga, "PAYMENT_PENDING — chờ webhook ⏳");
                    // KHÔNG gọi executeStep(saga) ở đây — dừng thật, chờ webhook.
                }

                case PAYMENT_DONE -> {
                    boolean failInventory = "INVENTORY".equals(saga.getSimulateFailAt());
                    ServiceResponse res = inventoryClient.reserveStock(saga, failInventory);

                    if (!res.isSuccess()) {
                        log.error("[Orchestrator] Inventory fail: {}", res.getMessage());
                        saga.setErrorMessage(res.getMessage());
                        startCompensation(saga);
                        return;
                    }
                    saga.updateStep(SagaStep.INVENTORY_RESERVED);
                    save(saga, "INVENTORY_RESERVED ✓");
                    executeStep(saga);
                }

                case INVENTORY_RESERVED -> {
                    // Tất cả bước xong → confirm order → COMPLETED
                    ServiceResponse res = orderClient.confirmOrder(saga.getSagaId());
                    if (!res.isSuccess()) {
                        log.error("[Orchestrator] confirmOrder fail: {}", res.getMessage());
                        saga.setErrorMessage(res.getMessage());
                        return; // để SagaRecoveryJob tự retry, không compensate
                    }

                    saga.updateStep(SagaStep.COMPLETED);
                    saga.setStatus(SagaStatus.COMPLETED);
                    save(saga, "COMPLETED ✓✓✓");
                    log.info("[Orchestrator] ═══ SAGA COMPLETED sagaId={} ═══", saga.getSagaId());
                }

                case PAYMENT_PENDING ->
                    log.info("[Orchestrator] sagaId={} đang chờ webhook, không làm gì thêm", saga.getSagaId());

                default -> log.warn("[Orchestrator] Unexpected step: {}", saga.getCurrentStep());
            }
        } catch (Exception e) {
            log.error("[Orchestrator] Exception tại step {}: {}", saga.getCurrentStep(), e.getMessage());
            saga.setErrorMessage(e.getMessage());
            startCompensation(saga);
        }
    }

    /**
     * Entry point MỚI — được PaymentController gọi vào khi nhận webhook
     * từ provider (qua PaymentService → gọi ngược lại Orchestrator).
     *
     * Đây là điểm "thức dậy" của saga đang ở PAYMENT_PENDING.
     */
    @Transactional
    public void resumeFromPaymentWebhook(String sagaId, boolean paymentSuccess, String errorMessage) {
        SagaState saga = sagaStateRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.error("[Orchestrator] resumeFromPaymentWebhook: không tìm thấy sagaId={}", sagaId);
            return;
        }

        if (saga.getCurrentStep() != SagaStep.PAYMENT_PENDING) {
            // Saga đã tiến/lùi khỏi PAYMENT_PENDING từ trước (webhook đến
            // trễ, hoặc gửi trùng) — bỏ qua, tránh xử lý sai trạng thái.
            log.warn("[Orchestrator] Webhook đến nhưng saga không còn ở PAYMENT_PENDING (đang ở {}), bỏ qua",
                saga.getCurrentStep());
            return;
        }

        if (!paymentSuccess) {
            log.error("[Orchestrator] Webhook báo Payment FAILED, sagaId={}: {}", sagaId, errorMessage);
            saga.setErrorMessage(errorMessage);
            startCompensation(saga);
            return;
        }

        log.info("[Orchestrator] Webhook báo Payment SUCCESS, resume sagaId={}", sagaId);
        saga.updateStep(SagaStep.PAYMENT_DONE);
        save(saga, "PAYMENT_DONE ✓ (từ webhook)");
        executeStep(saga);
    }

    // ── Compensation — chạy ngược lại ────────────────────────────

    /**
     * Compensation chạy NGƯỢC từ bước đang ở về bước 1
     * Chỉ compensate những bước đã THÀNH CÔNG
     * Bước chưa chạy → không cần compensate
     */
    public void startCompensation(SagaState saga) {
        log.warn("[Orchestrator] ═══ Bắt đầu COMPENSATION từ step={} ═══", saga.getCurrentStep());
        saga.setStatus(SagaStatus.COMPENSATING);
        save(saga, "COMPENSATING");
        compensateStep(saga);
    }

    private void compensateStep(SagaState saga) {
        log.warn("[Orchestrator] compensateStep: currentStep={}", saga.getCurrentStep());

        try {
            switch (saga.getCurrentStep()) {

                case INVENTORY_RESERVED, COMPENSATING_INVENTORY -> {
                    inventoryClient.releaseStock(saga.getSagaId());
                    saga.updateStep(SagaStep.COMPENSATING_PAYMENT);
                    save(saga, "COMPENSATING_PAYMENT");
                    compensateStep(saga);
                }

                case PAYMENT_DONE, PAYMENT_PENDING, COMPENSATING_PAYMENT -> {
                    // PAYMENT_PENDING cũng cần compensate qua refund —
                    // vì có thể webhook SUCCESS đã đến NHƯNG bị race với
                    // 1 lỗi khác trước khi ta kịp đọc lại (hiếm, nhưng an
                    // toàn hơn là refund() có idempotency riêng, refund 1
                    // payment chưa CHARGED sẽ tự fail rõ ràng, không sao).
                    paymentClient.refund(saga.getSagaId());
                    saga.updateStep(SagaStep.COMPENSATING_ORDER);
                    save(saga, "COMPENSATING_ORDER");
                    compensateStep(saga);
                }

                case ORDER_CREATED, COMPENSATING_ORDER, PENDING -> {
                    orderClient.cancelOrder(saga.getSagaId());
                    saga.updateStep(SagaStep.CANCELLED);
                    saga.setStatus(SagaStatus.CANCELLED);
                    save(saga, "CANCELLED");
                    log.warn("[Orchestrator] ═══ SAGA CANCELLED sagaId={} ═══", saga.getSagaId());
                }

                default -> log.warn("[Orchestrator] Không cần compensate step: {}", saga.getCurrentStep());
            }
        } catch (Exception e) {
            log.error("[Orchestrator] Compensation fail tại step {}: {}", saga.getCurrentStep(), e.getMessage());
            saga.setErrorMessage("Compensation fail: " + e.getMessage());
            saga.setStatus(SagaStatus.FAILED);
            sagaStateRepository.save(saga);
        }
    }

    // ── Helper ───────────────────────────────────────────────────

    private void save(SagaState saga, String logMsg) {
        sagaStateRepository.save(saga);
        log.info("[Orchestrator] SagaState saved: step={} — {}", saga.getCurrentStep(), logMsg);
    }

    @Transactional(readOnly = true)
    public SagaState getSaga(String sagaId) {
        return sagaStateRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("SAGA không tồn tại: " + sagaId));
    }
}
