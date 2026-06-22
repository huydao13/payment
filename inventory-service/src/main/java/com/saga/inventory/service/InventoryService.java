package com.saga.inventory.service;

import com.saga.inventory.dto.OrderRequest;
import com.saga.inventory.dto.ServiceResponse;
import com.saga.inventory.entity.Inventory;
import com.saga.inventory.entity.InventoryReservation;
import com.saga.inventory.repository.InventoryRepository;
import com.saga.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    /**
     * Trừ kho với Optimistic Locking + Idempotency + Retry
     *
     * Kết hợp 3 cơ chế:
     * 1. Idempotency (sagaId) → tránh trừ kho 2 lần khi retry
     * 2. Optimistic Locking (@Version) → tránh race condition
     * 3. @Retryable → tự động retry khi bị OptimisticLockException
     */
    @Transactional
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2, random = true)
    )
    public ServiceResponse reserveStock(OrderRequest request) {
        log.info("[Inventory Service] reserveStock() sagaId={}, qty={}", request.getSagaId(), request.getQuantity());

        // ── Idempotency check ────────────────────────────────────
        if (reservationRepository.existsById(request.getSagaId())) {
            InventoryReservation existing = reservationRepository.findById(request.getSagaId()).get();
            log.info("[Inventory Service] Đã reserve rồi (idempotent) sagaId={}", request.getSagaId());
            return ServiceResponse.ok("Đã reserve (idempotent)", existing.getSagaId());
        }

        // ── Simulate failure ─────────────────────────────────────
        if (request.isSimulateFail()) {
            return ServiceResponse.fail("Inventory Service: Simulated failure");
        }

        // ── Lấy inventory — Optimistic Locking bắt đầu từ đây ───
        Inventory inventory = inventoryRepository.findById(request.getProductId()).orElse(null);
        if (inventory == null) {
            log.warn("[Inventory Service] Sản phẩm không tồn tại: {}", request.getProductId());
            return ServiceResponse.fail("Sản phẩm không tồn tại: " + request.getProductId());
        }

        log.info("[Inventory Service] Kiểm tra kho: {} cái, cần: {} cái (version={})",
                inventory.getQuantity(), request.getQuantity(), inventory.getVersion());

        // ── Kiểm tra tồn kho ─────────────────────────────────────
        if (inventory.getQuantity() < request.getQuantity()) {
            return ServiceResponse.fail("Hết hàng! Còn " + inventory.getQuantity() + " cái, cần " + request.getQuantity());
        }

        // ── Trừ kho — @Version tự kiểm tra race condition ────────
        // Nếu 2 request cùng chạy đến đây:
        // → Request A save trước → version tăng lên
        // → Request B save sau → version không khớp → OptimisticLockException
        // → @Retryable tự động retry
        inventory.setQuantity(inventory.getQuantity() - request.getQuantity());
        inventoryRepository.save(inventory); // có thể throw OptimisticLockException

        // ── Lưu reservation ──────────────────────────────────────
        InventoryReservation reservation = new InventoryReservation(
                request.getSagaId(), request.getProductId(), request.getQuantity());
        reservationRepository.save(reservation);

        log.info("[Inventory Service] Reserve thành công! Còn {} cái", inventory.getQuantity());
        return ServiceResponse.ok("Reserve thành công", request.getSagaId());
    }

    /**
     * Compensation: Hoàn lại kho
     */
    @Transactional
    public ServiceResponse releaseStock(String sagaId) {
        log.info("[Inventory Service] releaseStock() sagaId={}", sagaId);

        return reservationRepository.findById(sagaId).map(reservation -> {
            if (reservation.getStatus() == InventoryReservation.ReservationStatus.RELEASED) {
                return ServiceResponse.ok("Đã hoàn kho rồi (idempotent)", sagaId);
            }
            Inventory inventory = inventoryRepository.findById(reservation.getProductId()).orElse(null);
            if (inventory == null) {
                log.error("[Inventory Service] Không tìm thấy sản phẩm {} để hoàn kho (dữ liệu không nhất quán)",
                    reservation.getProductId());
                return ServiceResponse.fail("Không tìm thấy sản phẩm để hoàn kho: " + reservation.getProductId());
            }
            inventory.setQuantity(inventory.getQuantity() + reservation.getQuantity());
            inventoryRepository.save(inventory);

            reservation.setStatus(InventoryReservation.ReservationStatus.RELEASED);
            reservationRepository.save(reservation);

            log.info("[Inventory Service] Đã hoàn {} cái về kho", reservation.getQuantity());
            return ServiceResponse.ok("Đã hoàn kho", sagaId);
        }).orElse(ServiceResponse.fail("Không tìm thấy reservation để hoàn kho"));
    }
}
