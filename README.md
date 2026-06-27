# SAGA Pattern Demo — Multi-module Spring Boot

Dự án mô phỏng SAGA Orchestration Pattern với 5 service: 1 orchestrator,
3 service nghiệp vụ, và 1 mock payment provider giả lập payment gateway
thật (gọi async, tự bắn webhook về).

## Kiến trúc

```
                          ┌──────────────┐
                          │   React UI   │
                          │  (catalog,   │
                          │  đặt hàng)   │
                          └──────┬───────┘
                                 │
                                 ▼
                     ┌────────────────────┐
                     │  Saga Orchestrator │  (8080)
                     │  - State Machine   │
                     │  - SagaRecoveryJob │
                     └──┬──────┬──────┬───┘
                        │      │      │
              ┌─────────┘      │      └─────────┐
              ▼                ▼                ▼
        ┌──────────┐    ┌──────────────┐  ┌───────────────┐
        │  Order   │    │   Payment    │  │   Inventory   │
        │ Service  │    │   Service    │  │    Service    │
        │  (8081)  │    │   (8082)     │  │    (8083)     │
        └────┬─────┘    └──────┬───────┘  └──────┬────────┘
             │                 │                 │
             ▼                 ▼                 ▼
        ┌─────────────────────────────────────────────┐
        │              PostgreSQL (1 instance)         │
        │  orderdb · paymentdb · inventorydb · sagadb  │
        └───────────────────────────────────────────────┘

                          Payment Service
                                 │
                    (async, sau khi commit)
                                 ▼
                   ┌─────────────────────────┐
                   │  Mock Payment Provider   │  (9081, repo riêng)
                   │  - xử lý bất đồng bộ     │
                   │  - tự gọi webhook về    │
                   └─────────────────────────┘
```

Mỗi service có **database PostgreSQL riêng** (database-per-service):
`sagadb`, `orderdb`, `paymentdb`, `inventorydb` — chạy trên cùng 1
Postgres instance, phân biệt theo tên database. `mock-payment-provider`
nằm ở repo riêng, nối vào hệ thống qua Docker bridge network.

## Flow chính

```
PENDING → ORDER_CREATED → PAYMENT_PENDING → PAYMENT_DONE → INVENTORY_RESERVED → COMPLETED
```

`PAYMENT_PENDING` là điểm khác biệt quan trọng so với thiết kế ban đầu —
Payment Service không tự chờ provider xử lý xong trong cùng request.
Nó lưu `Payment` ở trạng thái `PENDING`, trả lời Orchestrator ngay, rồi
**sau khi transaction commit xong**, mới gọi sang `mock-payment-provider`
(qua `PaymentEventListener`, `@Async` + `@TransactionalEventListener
(AFTER_COMMIT)`). Provider xử lý giả lập có độ trễ, rồi **tự gọi
webhook** về Payment Service để báo `CHARGED`/`FAILED`. Payment Service
nhận webhook, báo lại Orchestrator để resume saga từ `PAYMENT_PENDING`.

Đây là **Outbox pattern** — tách hẳn "ghi dữ liệu nội bộ" và "gọi
side-effect ra ngoài" thành 2 bước có thứ tự đảm bảo, để loại bỏ race
condition giữa lúc webhook về và lúc dữ liệu commit xong.

## Compensation (chạy ngược)

```
Lỗi tại bước N → Compensate từ N-1 về 1:
INVENTORY fail → releaseStock → refund → cancelOrder → CANCELLED
PAYMENT fail   → refund → cancelOrder → CANCELLED
ORDER fail     → cancelOrder → CANCELLED (không có gì để compensate trước đó)
```

State machine chi tiết hơn (xem `SagaStep.java`):

```
COMPENSATING_INVENTORY → COMPENSATING_PAYMENT → COMPENSATING_ORDER → CANCELLED
```

Nếu compensation cũng lỗi quá `MAX_RETRY` lần, saga kết thúc ở trạng
thái `FAILED` — cần xử lý thủ công.

## Lưới an toàn — `SagaRecoveryJob`

Job chạy mỗi 30 giây, phát hiện saga "stuck" (không cập nhật quá 2
phút). Riêng case `PAYMENT_PENDING`, job **chủ động hỏi lại** trạng
thái thật từ Payment Service (`GET /api/payments/status/{sagaId}`)
trước khi quyết định retry hay fail — để xử lý đúng trường hợp webhook
bị lỡ (đến trước khi Orchestrator kịp lưu `PAYMENT_PENDING`).

## Chạy local

### Yêu cầu

- Java 17+
- Maven 3.8+
- Docker + Docker Compose
- Node.js 20+ (cho UI React)

### Repo

Hệ thống nằm ở 2 repo riêng, nối qua Docker bridge network:
- `saga-demo` — Order/Payment/Inventory Service + Saga Orchestrator + Postgres
- `payment-provider` — Mock Payment Provider

### Bước 1 — Tạo `bridge-network` (1 lần)

```bash
docker network create bridge-network
```

### Bước 2 — Tạo file `.env` ở cả 2 repo

```bash
cp .env.example .env
```

`POSTGRES_USER`/`POSTGRES_PASSWORD` **phải khớp nhau** giữa 2 repo — vì
dùng chung 1 Postgres instance.

### Bước 3 — Tạo database + áp schema migration

pom.xml hiện chưa có Flyway/Liquibase — chạy các file SQL trong
`src/main/resources/db/migration/` của từng service **thủ công** vào
Postgres trước khi start service (`ddl-auto=validate` không tự sinh
schema).

```sql
CREATE DATABASE sagadb;
CREATE DATABASE orderdb;
CREATE DATABASE paymentdb;
CREATE DATABASE inventorydb;
CREATE DATABASE providerdb;
```

### Bước 4 — Chạy bằng Docker Compose

```bash
# repo saga-demo
docker compose up -d

# repo payment-provider
docker compose up -d
```

Log xem qua Dozzle tại `http://localhost:9999` — đọc được cả container
ở 2 repo vì dùng chung Docker socket trên VPS.

### Bước 5 — Chạy UI React

```bash
cd saga-ui
npm install
cp .env.example .env   # chỉnh VITE_ORCHESTRATOR_URL, VITE_INVENTORY_URL
npm run dev
```

## Swagger UI

| Service                  | URL                                    |
| ------------------------- | --------------------------------------- |
| SAGA Orchestrator         | http://localhost:8080/swagger-ui.html  |
| Order Service             | http://localhost:8081/swagger-ui.html  |
| Payment Service           | http://localhost:8082/swagger-ui.html  |
| Inventory Service         | http://localhost:8083/swagger-ui.html  |
| Mock Payment Provider     | http://localhost:9081/swagger-ui.html  |

## Test các scenario

Qua UI React (catalog, bấm "Đặt hàng") hoặc trực tiếp `curl`:

```bash
curl -X POST http://localhost:8080/api/saga/start \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "productId": "product-A",
    "quantity": 1,
    "amount": 300000,
    "simulateFailAt": null
  }'
```

### Scenario 1: Happy path

`simulateFailAt: null` → `currentStep: COMPLETED` sau khi webhook báo
`CHARGED` (mất vài giây, đúng `delayMs` cấu hình ở provider).

### Scenario 2: Payment fail

`simulateFailAt: "PAYMENT"` → compensate `cancelOrder` → `CANCELLED`.

### Scenario 3: Inventory fail

`simulateFailAt: "INVENTORY"` → compensate `refund` → `cancelOrder` →
`CANCELLED`.

### Scenario 4: Hết hàng

Đặt `quantity` > tồn kho hiện có (xem catalog `/api/products`) → tự
fail ở bước Inventory.

### Scenario 5: Cấu hình hành vi provider

```bash
curl -X POST http://localhost:9081/api/provider/config \
  -H "Content-Type: application/json" \
  -d '{"delayMs": 5000, "failRate": 30}'
```

Đổi `delayMs`/`failRate` áp dụng cho mọi giao dịch tiếp theo — dùng để
quan sát hành vi webhook bất đồng bộ rõ hơn trong demo.

## Các concept được implement

| Concept              | File                                      |
| --------------------- | ----------------------------------------- |
| SAGA State Machine    | `SagaOrchestrator.java`                   |
| SagaState (DB)        | `SagaState.java`                          |
| Scheduled Recovery    | `SagaRecoveryJob.java` — hỏi lại trạng thái thật khi nghi ngờ webhook bị lỡ |
| Idempotency Key       | `IdempotencyKey.java` (Payment), `existsBySagaId` (Order), `InventoryReservation` (Inventory) |
| Optimistic Locking    | `@Version` trong `Inventory.java`, kèm `@Retryable` tự retry khi `OptimisticLockException` |
| Outbox Pattern        | `PaymentInitiatedEvent` + `PaymentEventListener` (`@Async`, `AFTER_COMMIT`) |
| Webhook bất đồng bộ   | `mock-payment-provider` xử lý giả lập có `delayMs`, tự gọi `POST /api/payments/webhook` |
| Compensation          | `startCompensation()` / `compensateStep()` trong `SagaOrchestrator.java` |
