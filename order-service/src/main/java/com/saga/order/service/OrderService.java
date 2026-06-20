package com.saga.order.service;

import com.saga.order.dto.OrderRequest;
import com.saga.order.dto.ServiceResponse;
import com.saga.order.entity.Order;
import com.saga.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Tạo đơn hàng mới
     *
     * Đây là LOCAL @Transactional — chỉ quản lý DB của Order Service
     * Không liên quan gì đến Payment hay Inventory DB
     * → Đây là lý do cần SAGA để coordinate
     */
    @Transactional
    public ServiceResponse createOrder(OrderRequest request) {
        log.info("[Order Service] Tạo đơn hàng cho sagaId={}", request.getSagaId());

        // Simulate failure để test scenario
        if (request.isSimulateFail()) {
            log.warn("[Order Service] Simulate fail được bật!");
            return ServiceResponse.fail("Order Service: Simulated failure");
        }

        // Idempotency check — sagaId dùng làm idempotency key
        // Nếu đã tạo đơn cho sagaId này rồi (do retry) thì trả về kết quả cũ luôn
        if (orderRepository.existsBySagaId(request.getSagaId())) {
            Order existing = orderRepository.findBySagaId(request.getSagaId()).get();
            return ServiceResponse.ok("Đơn hàng đã tồn tại (idempotent)", existing.getOrderId());
        }

        // Tạo đơn mới
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(
                orderId,
                request.getSagaId(),
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                request.getAmount()
        );
        orderRepository.save(order);

        log.info("[Order Service] Tạo đơn thành công orderId={}", orderId);
        return ServiceResponse.ok("Tạo đơn hàng thành công", orderId);
    }

    /**
     * Compensation: Huỷ đơn hàng
     *
     * Được gọi khi Payment hoặc Inventory thất bại
     * Đây là "compensating transaction" — hoàn tác bước 1
     */
    @Transactional
    public ServiceResponse cancelOrder(String sagaId) {
        log.info("[Order Service] Huỷ đơn hàng sagaId={}", sagaId);

        return orderRepository.findBySagaId(sagaId).map(order -> {
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("[Order Service] Đã huỷ đơn orderId={}", order.getOrderId());
            return ServiceResponse.ok("Đã huỷ đơn hàng", order.getOrderId());
        }).orElse(ServiceResponse.fail("Không tìm thấy đơn hàng để huỷ"));
    }

    /**
     * Confirm đơn hàng — được gọi khi toàn bộ SAGA thành công
     */
    @Transactional
    public ServiceResponse confirmOrder(String sagaId) {
        return orderRepository.findBySagaId(sagaId).map(order -> {
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderRepository.save(order);
            log.info("[Order Service] Đã confirm đơn orderId={}", order.getOrderId());
            return ServiceResponse.ok("Đơn hàng đã xác nhận", order.getOrderId());
        }).orElse(ServiceResponse.fail("Không tìm thấy đơn hàng"));
    }
}
