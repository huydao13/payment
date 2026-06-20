package com.saga.orchestrator.enums;

public enum SagaStatus {
    RUNNING,       // Đang chạy bình thường
    COMPENSATING,  // Đang chạy compensation (rollback)
    COMPLETED,     // Thành công hoàn toàn
    CANCELLED,     // Đã compensation xong — kết thúc thất bại
    FAILED         // Quá nhiều retry → cần dev xử lý tay
}
