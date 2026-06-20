package com.saga.orchestrator.repository;

import com.saga.orchestrator.entity.SagaState;
import com.saga.orchestrator.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, String> {

    // Dùng bởi SagaRecoveryJob để tìm SAGA bị stuck
    List<SagaState> findByStatusInAndUpdatedAtBefore(
            List<SagaStatus> statuses,
            LocalDateTime before
    );
}
