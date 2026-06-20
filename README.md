# SAGA Pattern Demo — Multi-module Spring Boot

Dự án mô phỏng SAGA Orchestration Pattern với 3 microservice thực tế.
Lấy cảm hứng từ payment flow của TWINT.

## Kiến trúc

```
┌─────────────────────────────────────────┐
│       SAGA Orchestrator (8080)          │
│  - State Machine (SagaStep enum)        │
│  - SagaState lưu vào DB sau mỗi bước   │
│  - Scheduled Job recover stuck SAGAs   │
└──────┬──────────┬──────────┬────────────┘
       │          │          │
       ▼          ▼          ▼
  Order       Payment    Inventory
 Service      Service     Service
  (8081)       (8082)      (8083)
   H2 DB       H2 DB       H2 DB
               + Idem.    + Opt.Lock
               Key Table
```

## Flow chính

```
PENDING → ORDER_CREATED → PAYMENT_DONE → INVENTORY_RESERVED → COMPLETED
```

## Compensation (chạy ngược)

```
Lỗi tại bước N → Compensate từ N-1 về 1:
INVENTORY fail → releaseStock → refund → cancelOrder → CANCELLED
PAYMENT fail → refund → cancelOrder → CANCELLED
ORDER fail → cancelOrder → CANCELLED
```

## Chạy local

### Yêu cầu
- Java 17+
- Maven 3.8+

### Cách nhanh nhất — dùng script

```bash
chmod +x start-all.sh stop-all.sh test-scenarios.sh

./start-all.sh              # Build + chạy cả 5 service trong background
./test-scenarios.sh success # Test 1 scenario
./test-scenarios.sh all     # Test tất cả scenario liên tiếp
./stop-all.sh                # Dừng tất cả
```

Log của từng service nằm tại `logs/<service-name>.log` — mở để xem chi tiết flow SAGA chạy như thế nào (rất hữu ích để hiểu state machine).

### Cách thủ công — build và chạy từng service
```bash
mvn clean install -DskipTests
```

### Chạy từng service (mỗi terminal riêng)
```bash
# Terminal 1 — Order Service
cd order-service && mvn spring-boot:run

# Terminal 2 — Payment Service
cd payment-service && mvn spring-boot:run

# Terminal 3 — Inventory Service
cd inventory-service && mvn spring-boot:run

# Terminal 4 — SAGA Orchestrator (chạy sau cùng)
cd saga-orchestrator && mvn spring-boot:run
```

## Swagger UI

| Service | URL |
|---------|-----|
| SAGA Orchestrator | http://localhost:8080/swagger-ui.html |
| Order Service | http://localhost:8081/swagger-ui.html |
| Payment Service | http://localhost:8082/swagger-ui.html |
| Inventory Service | http://localhost:8083/swagger-ui.html |

## Test các scenario với Swagger

Vào http://localhost:8080/swagger-ui.html → POST /api/saga/start

### Scenario 1: Happy path
```json
{
  "userId": "user-001",
  "productId": "product-A",
  "quantity": 1,
  "amount": 300000,
  "simulateFailAt": null
}
```
Kết quả mong đợi: `currentStep: COMPLETED`

### Scenario 2: Payment fail
```json
{ "simulateFailAt": "PAYMENT", ... }
```
Kết quả: SAGA compensate cancelOrder → `currentStep: CANCELLED`

### Scenario 3: Inventory fail
```json
{ "simulateFailAt": "INVENTORY", ... }
```
Kết quả: refund → cancelOrder → `currentStep: CANCELLED`

### Scenario 4: Idempotency test
Chạy cùng 1 request 3 lần → Payment chỉ charge 1 lần

## Các concept được implement

| Concept | File |
|---------|------|
| SAGA State Machine | `SagaOrchestrator.java` |
| SagaState (DB) | `SagaState.java` |
| Scheduled Recovery | `SagaRecoveryJob.java` |
| Idempotency Key | `IdempotencyKey.java` (Payment) |
| Optimistic Locking | `@Version` trong `Inventory.java` |
| Compensation | `startCompensation()` trong Orchestrator |
| Local @Transactional | Mỗi service method |

## Mở rộng thêm

### Thêm lại Delivery Service (hoặc Notification Service)
1. Tạo module mới, ví dụ `delivery-service` (port 8084)
2. Thêm state mới vào `SagaStep` enum, ví dụ `DELIVERY_SCHEDULED` giữa `INVENTORY_RESERVED` và `COMPLETED`
3. Thêm `Client` tương ứng vào Orchestrator (xem cách `InventoryClient` được implement)
4. Thêm `case INVENTORY_RESERVED` gọi sang service mới, đổi `case` cũ thành bước tiếp theo
5. Thêm compensation tương ứng vào `compensateStep()` — nhớ thứ tự compensate phải NGƯỢC với thứ tự chạy chính

### Thêm scenario hết hàng
Đặt quantity > 10 (inventory chỉ có 10) → tự động fail ở bước Inventory
