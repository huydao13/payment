# TODO.md — Vấn đề đã phát hiện, chưa xử lý

Mức độ: 🔴 cao — nên làm trước phỏng vấn nếu còn thời gian
🟡 trung — nên biết rõ và giải thích được khi hỏi
⚪ thấp — chỉ cần biết tồn tại, không cần code

---

## 🔴 1. Compensation không có giới hạn retry riêng

**Vấn đề:** `SagaOrchestrator.compensateStep()` hiện tại:

```java
} catch (Exception e) {
    saga.setStatus(SagaStatus.FAILED);   // set FAILED NGAY
    sagaStateRepository.save(saga);
}
```

Nếu `refund()` hoặc `releaseStock()` lỗi tạm thời (network timeout 1 lần,
service tạm quá tải), saga bị đánh `FAILED` ngay — không có cơ hội tự
phục hồi, dù lỗi đó hoàn toàn có thể tự khỏi nếu thử lại sau vài giây.

So sánh: forward path (`executeStep`) được `SagaRecoveryJob` bảo vệ,
retry tối đa 3 lần trước khi fail. Compensation (quan trọng hơn vì
liên quan tiền/hàng) lại mong manh hơn forward path — bất đối xứng
không hợp lý.

**Cách làm:**
- Sửa `compensateStep()` để KHÔNG set `FAILED` ngay khi catch exception
  — chỉ log lỗi, giữ `status = COMPENSATING`, để `SagaRecoveryJob` resume
- `SagaRecoveryJob` cần xử lý case `COMPENSATING` giống cách xử lý
  `PAYMENT_PENDING` — thử gọi lại `compensateStep()`, tăng `retryCount`,
  chỉ set `FAILED` sau khi vượt `MAX_RETRY`

**File cần sửa:** `SagaOrchestrator.java`, `SagaRecoveryJob.java`

---

## 🔴 2. Optimistic locking chưa từng test thật với tải đồng thời

**Vấn đề:** `InventoryService.reserveStock()` dùng `@Version` +
`@Retryable` đúng theo lý thuyết, nhưng chưa từng chạy 2+ request
đồng thời thật để xem `OptimisticLockingFailureException` + retry có
thực sự xảy ra và xử lý đúng không.

Nếu phỏng vấn hỏi "bạn đã test race condition này chưa", câu trả lời
tốt nhất cần có log thật chứng minh retry đã xảy ra — không chỉ "code
đúng theo lý thuyết".

**Cách làm:**
- Viết script `k6` (hoặc đơn giản hơn: vài lệnh `curl` chạy đồng thời
  qua `&` trong bash) gửi 5-10 request `reserveStock` cùng lúc cho
  cùng 1 `productId` với tổng `quantity` vượt tồn kho thật
- Quan sát log `mock-payment-provider`... à nhầm, log `inventory-service`
  — tìm dòng `OptimisticLockingFailureException` và dòng retry thành
  công sau đó
- Chụp lại log này làm bằng chứng — đây chính là "demo" tốt nhất cho
  phần optimistic locking, hơn hẳn việc chỉ đọc code

**File liên quan:** `InventoryService.java` (không cần sửa code, chỉ
cần test và lưu log)

---

## 🟡 3. `idempotency_keys` không có TTL — phình bảng vô hạn

**Vấn đề:** Bảng `idempotency_keys` (Payment Service) lưu mãi mãi,
không bao giờ xoá. Theo thời gian, bảng lớn dần làm chậm
`findById(idempotencyKey)`.

**Cách làm (khi cần — không gấp cho demo):**
- Thêm `@Scheduled` job riêng, chạy định kỳ (1 lần/ngày), xoá các key
  đã ở trạng thái cuối (`SUCCESS`/`FAILED`) và `updatedAt` quá N ngày
  (ví dụ 30 ngày) — KHÔNG xoá key đang `PENDING`
- Cân nhắc thêm index trên `(status, updated_at)` để query xoá nhanh

---

## 🟡 4. Thiếu kiểm soát version API giữa các service

**Vấn đề:** 5 service độc lập (Order, Payment, Inventory,
Orchestrator, Provider), mỗi cái build/deploy riêng qua GitHub
Actions. Đổi DTO ở 1 service (thêm field bắt buộc, đổi tên field) mà
quên deploy đồng bộ phía gọi nó → lỗi runtime âm thầm, không bắt được
lúc compile vì các service không chia sẻ code DTO.

**Cách làm (khi cần — không gấp cho demo, nhưng nên nói được khi hỏi):**
- Contract testing (Pact) giữa các service — mỗi service định nghĩa
  "hợp đồng" API nó cung cấp, service gọi nó verify hợp đồng đó trong
  CI trước khi merge
- Hoặc đơn giản hơn cho quy mô nhỏ: shared DTO module (1 thư viện
  chung cả 5 service cùng dùng) — đổi 1 chỗ, tất cả service build lại
  cùng version mới, lỗi compile lộ ra ngay nếu không đồng bộ

**Câu trả lời mẫu khi được hỏi:** "Hiện tại tôi chưa làm contract
testing vì quy mô demo nhỏ — đây là rủi ro thật khi scale team, sẽ
cần Pact hoặc shared DTO module để bắt lỗi không tương thích API lúc
build/CI, không phải lúc runtime."

---

## ⚪ 5. Thiếu observability tập trung (chỉ có Dozzle xem log riêng lẻ)

**Vấn đề:** Dozzle chỉ xem log thô từng container riêng — không nối
được toàn bộ trace của 1 `sagaId` qua 5 service thành 1 view duy nhất.
Phải tự grep `sagaId` qua nhiều tab Dozzle để follow 1 giao dịch.

**Cách làm (production thật, không cần cho demo):**
- Distributed tracing: Zipkin hoặc Jaeger, mỗi service tự propagate
  trace ID qua header HTTP
- Hoặc tối thiểu: structured logging (JSON log) + correlation ID đẩy
  vào ELK stack hoặc Grafana Loki, search theo `sagaId` xem hết log
  liên quan trong 1 lần

**Câu trả lời mẫu khi được hỏi:** "Hiện tại tôi dùng Dozzle để xem log
nhanh lúc demo — production thật sẽ cần distributed tracing (Zipkin/
Jaeger) hoặc structured logging tập trung để follow 1 sagaId qua toàn
bộ 5 service trong 1 view."

---

## Ghi chú thứ tự ưu tiên

Nếu chỉ còn ít thời gian trước phỏng vấn, làm theo thứ tự:
1. Vấn đề 2 (load test optimistic locking) — chỉ cần test + lưu log,
   không cần sửa code, giá trị cao nhất cho demo
2. Vấn đề 1 (compensation retry) — sửa code thật, thể hiện hiểu sâu
3. Vấn đề 3, 4, 5 — chỉ cần đọc và chuẩn bị câu trả lời mẫu, không
   cần code
