package com.saga.orchestrator.enums;

/**
 * SAGA State Machine — toàn bộ các state có thể có
 *
 * Flow chính (happy path):
 * PENDING → ORDER_CREATED → PAYMENT_DONE → INVENTORY_RESERVED → COMPLETED
 *
 * Compensation flow (khi có lỗi):
 * COMPENSATING_INVENTORY → COMPENSATING_PAYMENT → COMPENSATING_ORDER → CANCELLED
 *
 * Tại sao cần lưu từng step?
 * → Server crash bất kỳ lúc nào → scheduled job đọc DB → resume đúng chỗ
 * → Không chạy lại từ đầu → không double charge
 */
public enum SagaStep {

    // ── Happy path ──────────────────────────────────────────────
    PENDING,               // SAGA vừa được tạo, chưa làm gì
    ORDER_CREATED,         // Order Service tạo đơn thành công
    PAYMENT_PENDING,

    PAYMENT_DONE,          // Payment Service trừ tiền thành công
    INVENTORY_RESERVED,    // Inventory Service giữ hàng thành công — bước cuối trước COMPLETED
    COMPLETED,             // Tất cả xong — SAGA kết thúc thành công

    // ── Compensation steps (chạy ngược) ─────────────────────────
    COMPENSATING_INVENTORY,  // Hoàn lại kho
    COMPENSATING_PAYMENT,    // Hoàn tiền cho khách
    COMPENSATING_ORDER,      // Huỷ đơn hàng
    CANCELLED,               // SAGA kết thúc thất bại — đã compensation xong

    // ── Special states ───────────────────────────────────────────
    FAILED                   // Quá nhiều lần retry → cần xử lý tay
}
