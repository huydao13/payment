# SAGA Pattern Demo — Multi-module Spring Boot

Dự án mô phỏng SAGA Orchestration Pattern với 4 microservice thực tế (1 orchestrator + 3 service nghiệp vụ).
Lấy cảm hứng từ payment flow của TWINT.

## Kiến trúc

```
┌─────────────────────────────────────────┐
│       SAGA Orchestrator (8080)          │
│  - State Machine (SagaStep enum)        │
│  - SagaState lưu vào DB sau mỗi bước    │
│  - Scheduled Job recover stuck SAGAs    │
└──────┬──────────┬──────────┬────────────┘
       │          │          │
       ▼          ▼          ▼
  Order       Payment    Inventory
 Service      Service     Service
  (8081)       (8082)      (8083)
 PostgreSQL   PostgreSQL  PostgreSQL
  orderdb      paymentdb  + Idem.Key    inventorydb
                            Table       + Optimistic
                                          Lock (@Version)
```

Mỗi service có **database PostgreSQL riêng** (database-per-service): `sagadb`, `orderdb`, `paymentdb`, `inventorydb` — tất cả chạy trên cùng 1 Postgres instance (container `postgres:16-alpine`), phân biệt theo tên database.

Schema được tạo bằng các file SQL trong `src/main/resources/db/migration/` của từng service (đặt theo convention `V1__...sql`, `V2__...sql`). `spring.jpa.hibernate.ddl-auto=validate` — Hibernate **không** tự sinh schema, chỉ validate entity khớp với schema đã có sẵn trong DB.

> ⚠️ Lưu ý: pom.xml hiện tại **chưa có dependency Flyway/Liquibase**, nên các file migration này hiện không được tool nào tự động áp dụng khi service khởi động. Cần chạy SQL này thủ công vào Postgres trước khi start service, hoặc thêm Flyway vào dependency nếu muốn tự động hoá.

## Flow chính

```
PENDING → ORDER_CREATED → PAYMENT_DONE → INVENTORY_RESERVED → COMPLETED
```

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

## Chạy local

### Yêu cầu

- Java 17+
- Maven 3.8+
- Docker + Docker Compose (để chạy PostgreSQL, hoặc tự cài Postgres 16 local)

### Bước 1 — Tạo file `.env`

```bash
cp .env.example .env
```

Sửa `POSTGRES_PASSWORD` nếu cần. File `.env.example`:

```
POSTGRES_USER=payment
POSTGRES_PASSWORD=changeme

ORDER_TAG=latest
PAYMENT_TAG=latest
INVENTORY_TAG=latest
ORCHESTRATOR_TAG=latest
```

### Bước 2 — Build

```bash
mvn clean install -DskipTests
```

### Bước 3a — Chạy bằng Docker Compose (dùng image đã build sẵn trên GHCR)

```bash
docker compose up -d
```

`docker-compose.yml` pull image từ `ghcr.io/huydao13/<service>:latest` cho từng service, kèm 1 container `postgres:16-alpine` và `dozzle` (xem log container qua web UI tại `http://localhost:9999`).

> Compose hiện chưa tự tạo 4 database (`sagadb`, `orderdb`, `paymentdb`, `inventorydb`) và chưa tự áp schema migration — cần script `init-db.sql` (được mount vào container Postgres) tạo đủ 4 database, và áp các file `V1__*.sql`/`V2__*.sql` của từng service thủ công trước khi service start, nếu không các service sẽ lỗi do `ddl-auto=validate` không thấy bảng.

### Bước 3b — Hoặc chạy từng service thủ công (mỗi terminal riêng)

Cần Postgres đang chạy ở `localhost:5432` với user/password khớp `application.properties` (mặc định `payment` / `1234`), và đã tạo đủ 4 database + áp schema migration.

```bash
# Terminal 1 — Order Service
cd order-service && mvn spring-boot:run

# Terminal 2 — Payment Service
cd payment-service && mvn spring-boot:run

# Terminal 3 — Inventory Service
cd inventory-service && mvn spring-boot:run

# Terminal 4 — SAGA Orchestrator (chạy sau cùng, vì gọi sang 3 service trên)
cd saga-orchestrator && mvn spring-boot:run
```

Không có script `start-all.sh` / `stop-all.sh` / `test-scenarios.sh` trong repo hiện tại — test scenario thực hiện qua Swagger UI hoặc `curl` (xem bên dưới).

## Swagger UI

| Service           | URL                                     |
| ----------------- | ---------------------------------------- |
| SAGA Orchestrator | http://localhost:8080/swagger-ui.html   |
| Order Service     | http://localhost:8081/swagger-ui.html   |
| Payment Service   | http://localhost:8082/swagger-ui.html   |
| Inventory Service | http://localhost:8083/swagger-ui.html   |

## Test các scenario

Vào `http://localhost:8080/swagger-ui.html` → `POST /api/saga/start`, hoặc dùng `curl`:

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

```json
{ "simulateFailAt": null, "productId": "product-A", "quantity": 1, "amount": 300000 }
```

Kết quả mong đợi: `currentStep: COMPLETED`

### Scenario 2: Payment fail

```json
{ "simulateFailAt": "PAYMENT", ... }
```

Kết quả: compensate `cancelOrder` → `currentStep: CANCELLED`

### Scenario 3: Inventory fail

```json
{ "simulateFailAt": "INVENTORY", ... }
```

Kết quả: compensate `refund` → `cancelOrder` → `currentStep: CANCELLED`

### Scenario 4: Idempotency test

Gửi cùng 1 request 3 lần liên tiếp (cùng `sagaId` nếu test trực tiếp vào từng service, hoặc gọi `/api/saga/start` 3 lần — mỗi lần có `sagaId` mới nên sẽ tạo 3 saga độc lập; để test idempotency thật, gọi trực tiếp `POST /api/payments/charge` hoặc `POST /api/inventory/reserve` với cùng `sagaId` 3 lần) → Payment chỉ charge 1 lần, Inventory chỉ trừ kho 1 lần.

### Scenario 5: Hết hàng

Đặt `quantity` > số lượng tồn kho hiện có cho `productId` đó (xem seed data ở `V2__seed_sample_products.sql`: `product-A` = 10 cái, `product-B` = 5 cái) → tự động fail ở bước Inventory.

### Kiểm tra số dư / reset dữ liệu test

```bash
curl http://localhost:8082/api/payments/balance      # xem số dư hiện tại
curl -X POST http://localhost:8082/api/payments/reset # reset về 2.000.000đ
curl -X DELETE http://localhost:8080/api/saga/reset    # xoá toàn bộ SagaState
```

> ⚠️ `accountBalance` trong `PaymentService` hiện là biến `static long` lưu trong RAM (không lưu Postgres), nên giá trị này **mất khi service restart** và không đồng bộ thật với bảng `payments`. Phù hợp để demo, chưa phù hợp cho production.

## Các concept được implement

| Concept              | File                                      |
| --------------------- | ----------------------------------------- |
| SAGA State Machine    | `SagaOrchestrator.java`                   |
| SagaState (DB)        | `SagaState.java`                          |
| Scheduled Recovery    | `SagaRecoveryJob.java` (chạy mỗi 30s, max 3 lần retry, ngưỡng stuck 2 phút) |
| Idempotency Key       | `IdempotencyKey.java` (Payment), `existsBySagaId` (Order), `InventoryReservation` (Inventory) |
| Optimistic Locking    | `@Version` trong `Inventory.java`, kèm `@Retryable` tự retry 3 lần khi `OptimisticLockException` |
| Compensation          | `startCompensation()` / `compensateStep()` trong `SagaOrchestrator.java` |
| Local `@Transactional`| Mỗi service method (orchestrator's `startSaga`/`executeStep` **cố ý không** dùng `@Transactional` — xem comment trong code) |

## Mở rộng thêm

### Thêm lại Delivery Service (hoặc Notification Service)

1. Tạo module mới, ví dụ `delivery-service` (port 8084), với database riêng `deliverydb`
2. Thêm state mới vào `SagaStep` enum, ví dụ `DELIVERY_SCHEDULED` giữa `INVENTORY_RESERVED` và `COMPLETED`
3. Thêm `Client` tương ứng vào Orchestrator (xem cách `InventoryClient` được implement)
4. Sửa `case INVENTORY_RESERVED` trong `executeStep()` để gọi sang service mới, đổi case cũ thành bước tiếp theo
5. Thêm compensation tương ứng vào `compensateStep()` — nhớ thứ tự compensate phải **ngược** với thứ tự chạy chính

### Thêm scenario hết hàng

Đặt `quantity` > tồn kho hiện có (xem seed data) → tự động fail ở bước Inventory, không cần code thêm gì.

## Việc còn thiếu / cần làm tiếp

- [ ] Thêm Flyway/Liquibase vào pom.xml để tự động áp schema migration khi service start
- [ ] Script `init-db.sql` tạo đủ 4 database cho container Postgres trong `docker-compose.yml`
- [ ] Chuyển `PaymentService.accountBalance` từ static field sang lưu thật trong Postgres, có lock đồng bộ khi cộng/trừ
- [ ] Script tiện ích để build & chạy local nhanh (hiện chưa có `start-all.sh`/`stop-all.sh`/`test-scenarios.sh`)
