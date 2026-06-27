package com.saga.orchestrator.job;

import com.saga.orchestrator.client.PaymentClient;
import com.saga.orchestrator.dto.ServiceResponse;
import com.saga.orchestrator.entity.SagaState;
import com.saga.orchestrator.enums.SagaStatus;
import com.saga.orchestrator.enums.SagaStep;
import com.saga.orchestrator.repository.SagaStateRepository;
import com.saga.orchestrator.service.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled Job — "người gác đêm" của SAGA
 *
 * Chạy mỗi 30 giây, tìm SAGA bị stuck vì:
 * → Server crash giữa chừng
 * → Service down tạm thời
 * → Network timeout
 *
 * Nếu retryCount < MAX_RETRY → resume SAGA
 * Nếu retryCount >= MAX_RETRY → đánh dấu FAILED → alert team
 *
 * Đây là lý do tại sao phải lưu SagaState sau mỗi bước!
 * Không có SagaState thì job này không biết resume từ đâu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaRecoveryJob {

    private final SagaStateRepository sagaStateRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final PaymentClient paymentClient;

    private static final int MAX_RETRY = 3;
    private static final int STUCK_THRESHOLD_MINUTES = 2;

    /**
     * Scheduled Job — "người gác đêm" của SAGA
     *
     * Chạy mỗi 30 giây, tìm SAGA bị stuck vì:
     * → Server crash giữa chừng
     * → Service down tạm thời
     * → Network timeout
     * → Webhook bị lỡ (race condition giữa lúc lưu PAYMENT_PENDING và
     *   lúc webhook từ provider tới — xem NOTES.md)
     *
     * Riêng case PAYMENT_PENDING: KHÔNG chỉ ngồi log chờ webhook như
     * trước — chủ động hỏi lại payment-service xem trạng thái thật ra
     * sao (CHARGED/FAILED/vẫn PENDING) trước khi quyết định retry/fail.
     * Đây sửa lỗi "lưới an toàn rỗng": trước đây nếu webhook bị lỡ,
     * job này retry 3 lần vô nghĩa rồi set FAILED, dù payment thực ra
     * đã charge thành công từ lâu.
     */
    @Scheduled(fixedDelay = 30_000)
    public void recoverStuckSagas() {
        LocalDateTime stuckThreshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);

        // Tìm SAGA bị stuck
        List<SagaState> stuckSagas = sagaStateRepository
                .findByStatusInAndUpdatedAtBefore(
                        List.of(SagaStatus.RUNNING, SagaStatus.COMPENSATING),
                        stuckThreshold
                );

        if (stuckSagas.isEmpty()) return;

        log.warn("[Recovery Job] Phát hiện {} SAGA bị stuck!", stuckSagas.size());

        for (SagaState saga : stuckSagas) {
            log.warn("[Recovery Job] SAGA stuck: sagaId={}, step={}, retryCount={}",
                    saga.getSagaId(), saga.getCurrentStep(), saga.getRetryCount());

            // ── Riêng PAYMENT_PENDING: hỏi lại trạng thái thật trước ──
            // GET đơn giản, không giữ thread chờ (khác hẳn Thread.sleep
            // retry tại request gốc — đây chạy trong job nền, không
            // chiếm Tomcat thread của request HTTP thật nào).
            if (saga.getCurrentStep() == SagaStep.PAYMENT_PENDING) {
                if (tryResumeFromActualPaymentStatus(saga)) {
                    continue; // đã resume xong, không cần retry/fail nữa
                }
                // Vẫn thực sự PENDING — rơi xuống logic retryCount như cũ
            }

            if (saga.getRetryCount() >= MAX_RETRY) {
                // Quá nhiều lần retry → cần dev xử lý tay
                log.error("[Recovery Job] SAGA quá nhiều lần retry → FAILED! sagaId={}", saga.getSagaId());
                saga.setStatus(SagaStatus.FAILED);
                saga.setErrorMessage("Quá " + MAX_RETRY + " lần retry — cần xử lý thủ công");
                sagaStateRepository.save(saga);
                // TODO: Gửi alert email/Slack cho team
                continue;
            }

            // Tăng retry count và resume
            saga.setRetryCount(saga.getRetryCount() + 1);
            sagaStateRepository.save(saga);

            try {
                if (saga.getStatus() == SagaStatus.RUNNING) {
                    log.info("[Recovery Job] Resume SAGA từ step={}", saga.getCurrentStep());
                    sagaOrchestrator.executeStep(saga);
                } else if (saga.getStatus() == SagaStatus.COMPENSATING) {
                    log.info("[Recovery Job] Resume COMPENSATION từ step={}", saga.getCurrentStep());
                    sagaOrchestrator.startCompensation(saga);
                }
            } catch (Exception e) {
                log.error("[Recovery Job] Resume thất bại sagaId={}: {}", saga.getSagaId(), e.getMessage());
            }
        }
    }

    /**
     * Hỏi payment-service trạng thái thật của payment ứng với saga
     * này. Nếu đã CHARGED hoặc FAILED (webhook bị lỡ, nhưng kết quả
     * thật đã có từ lâu) → tự resume ngay, KHÔNG đợi thêm retry vô
     * nghĩa nào nữa.
     *
     * @return true nếu đã resume xong (CHARGED hoặc FAILED), false
     *         nếu vẫn thực sự PENDING (chưa có gì để resume).
     */
    private boolean tryResumeFromActualPaymentStatus(SagaState saga) {
        ServiceResponse paymentStatus = paymentClient.getStatus(saga.getSagaId());

        if (!paymentStatus.isSuccess()) {
            // Không hỏi được (network lỗi, payment-service down...) —
            // không coi đây là CHARGED/FAILED, để vòng sau thử lại.
            log.warn("[Recovery Job] Không hỏi được trạng thái payment cho sagaId={}: {}",
                saga.getSagaId(), paymentStatus.getMessage());
            return false;
        }

        String status = paymentStatus.getMessage();

        if ("CHARGED".equals(status)) {
            log.warn("[Recovery Job] Webhook bị lỡ nhưng payment đã CHARGED — tự resume sagaId={}", saga.getSagaId());
            sagaOrchestrator.resumeFromPaymentWebhook(saga.getSagaId(), true, null);
            return true;
        }

        if ("FAILED".equals(status)) {
            log.warn("[Recovery Job] Webhook bị lỡ nhưng payment đã FAILED — tự resume compensation sagaId={}", saga.getSagaId());
            sagaOrchestrator.resumeFromPaymentWebhook(saga.getSagaId(), false,
                "Phát hiện qua Recovery Job: payment FAILED");
            return true;
        }

        // status == "PENDING" — đúng nghĩa, thực sự vẫn đang chờ provider.
        return false;
    }
}
