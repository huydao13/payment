# NOTES.md — Webhook race condition: Payment chưa commit khi webhook về

## Triệu chứng

`PaymentService.handleWebhook()` báo lỗi `Webhook cho sagaId không tồn
tại` dù log cho thấy `charge()` đã chạy và forward sang provider
trước đó.

## Nguyên nhân gốc

`charge()` gọi `providerClient.charge()` (network call ra ngoài)
**trong cùng 1 transaction** với việc lưu `Payment` vào DB. Provider
xử lý async và tự bắn webhook về ngay khi xong — nếu provider xử lý
nhanh hơn transaction gốc kịp commit, webhook đọc DB sẽ thấy `Payment`
**chưa tồn tại**.

Đây là race condition giữa 2 luồng độc lập:
- Thread A: request `charge()` gốc — gọi provider, rồi lưu DB, rồi commit
- Thread B: webhook callback từ provider — đọc DB ngay khi tới, không
  đợi transaction A

Bài học chung: **không bao giờ gọi service ngoài (network call) trong
khi đang giữ 1 transaction mở** — không kiểm soát được transaction đó
commit lúc nào so với network call đó hoàn thành lúc nào. Đây là vấn
đề thật mọi hệ thống payment gặp (Stripe, VNPay cũng vậy), không phải
bug riêng của demo này.

---

## Cách 1 — Đảo thứ tự: lưu Payment trước, gọi provider sau

**Cách làm:** Trong `charge()`, `paymentRepository.save(payment)`
chạy **trước** `providerClient.charge()`, không phải sau.

**Ưu điểm:** Sửa nhanh nhất, 1 dòng thay đổi vị trí code.

**Tradeoff:**
- Chỉ **giảm xác suất**, không loại bỏ hoàn toàn. Vì toàn bộ method
  vẫn nằm trong 1 `@Transactional`, transaction chỉ thực sự commit
  khi method `return` — nếu provider xử lý đủ nhanh để webhook tới
  *trước khi* `charge()` return xong, vẫn có thể race (đọc dữ liệu
  chưa commit từ transaction khác, tuỳ isolation level).
- Vẫn cần thêm retry-with-delay ở `handleWebhook()` (thử lại vài lần,
  cách nhau 200ms) để vá thêm — không đảm bảo 100%.

**Khi nào đủ dùng:** Demo/test nội bộ, `delayMs` của provider đủ lớn
(vài giây) để xác suất race gần như bằng 0. Không nên dùng cho
production thật.

---

## Cách 2 — Message queue trung gian (Kafka/RabbitMQ)

**Cách làm:** Webhook không xử lý trực tiếp — đẩy message vào queue.
1 consumer riêng xử lý tuần tự, tự retry nếu chưa tìm thấy Payment
(message vẫn còn trong queue, không mất).

**Ưu điểm:** Đảm bảo thứ tự xử lý đúng, có retry/dead-letter queue
sẵn có, chịu được tải cao, đúng kiến trúc hệ thống lớn thật.

**Tradeoff:**
- Thêm hẳn 1 hạ tầng mới (Kafka hoặc RabbitMQ) — phải deploy, vận
  hành, monitor thêm 1 service nữa.
- Độ phức tạp tăng mạnh so với quy mô hiện tại (demo/học pattern) —
  overkill nếu chỉ có vài service nhỏ.
- Cần thêm kiến thức vận hành message broker (partition, consumer
  group, offset...) — chi phí học tập cao hơn 2 cách còn lại.

**Khi nào nên dùng:** Hệ thống thật có nhiều webhook đến từ nhiều
provider khác nhau, cần throughput cao, cần audit log đầy đủ mọi
event. Không cần cho demo/học pattern quy mô nhỏ.

---

## Cách 3 — Outbox pattern / `@TransactionalEventListener(AFTER_COMMIT)`

**Cách làm:** `charge()` chỉ lưu `Payment` và publish 1 event nội bộ
(Spring `ApplicationEvent`), KHÔNG gọi provider trực tiếp. Một
listener riêng dùng `@TransactionalEventListener(phase =
AFTER_COMMIT)` chỉ chạy **sau khi** transaction gốc đã commit thành
công — lúc đó mới thực sự gọi `providerClient.charge()`.

**Ưu điểm:**
- Loại bỏ **hoàn toàn** race condition về mặt cấu trúc — provider
  không còn cách nào được gọi trước khi Payment commit, vì Spring tự
  đảm bảo thứ tự này.
- Không cần thêm hạ tầng mới (không cần Kafka/Rabbit) — chỉ dùng
  tính năng có sẵn của Spring.
- Đúng nguyên tắc thiết kế hệ thống production: tách hẳn "ghi dữ liệu
  nội bộ" và "gọi side-effect ra ngoài" thành 2 bước riêng.

**Tradeoff:**
- Cần hiểu đúng Spring transaction lifecycle (`AFTER_COMMIT`,
  `Propagation.REQUIRES_NEW` cho listener) — dễ làm sai nếu chưa quen
  pattern này, ví dụ quên `REQUIRES_NEW` sẽ khiến listener không có
  transaction riêng để tự update khi provider lỗi.
- Nếu publisher (`charge()`) không thực sự chạy trong 1 transaction
  thật (ví dụ gọi từ code không có `@Transactional`, hoặc unit test
  không mock đúng), event sẽ **không bao giờ được publish** — lỗi âm
  thầm, khó debug nếu không biết rõ cơ chế.
- **Độ trễ phát hiện lỗi provider tăng:** response `"PENDING"` trả về
  Orchestrator *trước khi* biết provider có gọi được hay không (vì
  việc gọi provider xảy ra sau, trong listener). Nếu provider down
  ngay lúc đó, Orchestrator không biết ngay — phải đợi
  `SagaRecoveryJob` phát hiện saga "stuck" ở `PAYMENT_PENDING` quá
  ngưỡng thời gian (`STUCK_THRESHOLD_MINUTES`, mặc định 2 phút) rồi
  mới retry/set FAILED. Đổi lại an toàn dữ liệu, chấp nhận phát hiện
  lỗi network chậm hơn.

**Khi nào nên dùng:** Đây là lựa chọn hợp lý nhất cho quy mô hiện tại
— đúng kiến trúc, không cần hạ tầng mới, dễ giải thích trong phỏng
vấn (cho thấy hiểu sâu transaction boundary và side-effect ordering).

---

## Quyết định đã chọn (cập nhật khi áp dụng)

- [ ] Cách 1 (tạm, để test nhanh)
- [ ] Cách 2 (nếu sau này mở rộng nhiều provider/webhook)
- [ ] Cách 3 (khuyến nghị — đã áp dụng ngày: ___________)
