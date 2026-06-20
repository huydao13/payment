package com.saga.orchestrator.job;

import com.saga.orchestrator.entity.SagaState;
import com.saga.orchestrator.enums.SagaStatus;
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

    private static final int MAX_RETRY = 3;
    private static final int STUCK_THRESHOLD_MINUTES = 2;

    /**
     * Chạy mỗi 30 giây
     * Tìm SAGA đang RUNNING hoặc COMPENSATING mà không được update > 2 phút
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
}
