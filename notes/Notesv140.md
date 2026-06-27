## Vấn đề 2: @Async cho PaymentEventListener — giảm "thời gian giữ máy"

### Nguyên nhân

`PaymentEventListener.onPaymentInitiated()` dùng
`@TransactionalEventListener(phase = AFTER_COMMIT)` — đảm bảo chạy
SAU KHI transaction lưu Payment đã commit, nhưng KHÔNG đảm bảo chạy
trên thread riêng. Mặc định nó vẫn chạy trên CÙNG thread với request
HTTP gốc (request `POST /charge` mà Orchestrator gửi sang).

Ví dụ dễ hiểu: bạn gọi điện cho tổng đài đặt pizza. Tổng đài đáng lẽ
ghi nhận đơn rồi trả lời ngay "đã ghi nhận, mã ABC" để bạn cúp máy.
Nhưng vì thiếu @Async, tổng đài lại GIỮ MÁY VỚI BẠN trong lúc TỰ HỌ
gọi sang bưu cục (provider) — bạn phải đứng nghe họ gọi điện cho người
khác suốt 2-3 giây, dù việc đó không liên quan trực tiếp tới câu trả
lời bạn cần ngay lúc này.

Hậu quả đo được qua log thật: khoảng cách từ lúc Orchestrator gọi
`paymentClient.charge()` tới lúc nhận được response kéo dài tới
**3 giây** (đáng lẽ chỉ vài chục ms) — đủ lâu để webhook từ provider
(đến sau đúng 2 giây delay) VƯỢT MẶT lúc Orchestrator kịp lưu
`PAYMENT_PENDING` vào DB. Kết quả: webhook tới khi saga vẫn còn ghi
"ORDER_CREATED" → bị từ chối với lỗi "saga không còn ở
PAYMENT_PENDING".

### Biện pháp khắc phục

Thêm `@Async` vào method listener:

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onPaymentInitiated(PaymentInitiatedEvent event) { ... }
```

`@Async` đẩy việc gọi provider sang 1 thread pool RIÊNG, tách hẳn
khỏi thread đang xử lý request HTTP gốc. Request `charge()` trả lời
NGAY sau khi lưu Payment + publish event (vài chục ms), không phải
đợi luôn cả phần gọi provider.

**Điều kiện cần:** `@Async` chỉ hoạt động nếu app có `@EnableAsync`
trong file config (`PaymentConfig.java`) — thiếu dòng này, `@Async`
bị Spring bỏ qua âm thầm, không báo lỗi.

**Giới hạn của cách fix này:** chỉ RÚT NGẮN khoảng thời gian race
(từ 3 giây xuống vài chục ms), KHÔNG loại bỏ hoàn toàn khả năng race
— về lý thuyết vẫn có thể có khoảng thời gian (dù rất nhỏ) khiến
webhook tới sớm hơn. Đây là lý do cần thêm lưới an toàn ở Vấn đề 3.

---

## Vấn đề 3: SagaRecoveryJob hiện tại là "lưới an toàn rỗng"

### Nguyên nhân

`SagaRecoveryJob` chạy định kỳ, phát hiện saga "stuck" ở
`PAYMENT_PENDING` quá `STUCK_THRESHOLD_MINUTES` (2 phút), nhưng khi
resume, code hiện tại với case `PAYMENT_PENDING` CHỈ LÀM:

```java
case PAYMENT_PENDING ->
    log.info("[Orchestrator] đang chờ webhook, không làm gì thêm");
```

Tức là: job retry 3 lần, mỗi lần CHỈ LOG, không hề kiểm tra xem
payment thực ra đã CHARGED hay chưa. Sau 3 lần vô nghĩa, nó set
saga = FAILED — NGAY CẢ KHI payment thực ra đã charge thành công từ
lâu (như log thật cho thấy: bảng `payments` ghi CHARGED, nhưng
Orchestrator vẫn báo FAILED vì chưa từng hỏi lại).

Ví dụ dễ hiểu: đây giống việc có người trực điện thoại 24/7 để nghe
khi có ai gọi báo tin, nhưng KHÔNG BAO GIỜ chủ động gọi đi hỏi xem
việc đã xong chưa — chỉ ngồi chờ chuông reo. Nếu chuông không reo
(vì lý do nào đó cuộc gọi bị lỡ), người đó cứ ngồi chờ đến hết giờ
rồi báo "thất bại" — dù việc thực ra đã xong từ lâu, chỉ là không
ai gọi báo.

### Biện pháp khắc phục

Cho `SagaRecoveryJob` CHỦ ĐỘNG hỏi lại trạng thái thật từ
`payment-service` (qua 1 endpoint mới `GET /api/payments/status/{sagaId}`)
trước khi quyết định retry hay fail:

```java
if (saga.getCurrentStep() == SagaStep.PAYMENT_PENDING) {
    ServiceResponse paymentStatus = paymentClient.getStatus(saga.getSagaId());

    if ("CHARGED".equals(paymentStatus.getMessage())) {
        // Webhook bị lỡ nhưng payment thực ra đã xong — tự resume
        sagaOrchestrator.resumeFromPaymentWebhook(saga.getSagaId(), true, null);
        continue;
    }
    if ("FAILED".equals(paymentStatus.getMessage())) {
        sagaOrchestrator.resumeFromPaymentWebhook(saga.getSagaId(), false, "Phát hiện qua Recovery Job");
        continue;
    }
    // Vẫn PENDING thật — tiếp tục đợi như cũ
}
```

**Quan trọng — KHÔNG dùng Thread.sleep/retry-tại-chỗ:** Ban đầu có ý
tưởng cho Orchestrator tự `Thread.sleep()` rồi đọc lại DB ngay trong
`resumeFromPaymentWebhook()` — đây là Ý TƯỞNG SAI, đã loại bỏ. Lý do:
`Thread.sleep` trong 1 request/transaction giữ nguyên 1 thread +
1 connection DB "đứng yên" suốt thời gian chờ — nếu nhiều webhook
tới đồng thời, dễ làm CẠN connection pool/Tomcat thread pool, tạo
ra đúng loại bottleneck khác (đặc biệt nguy hiểm khi scale lên
nhiều giao dịch đồng thời).

Cách đúng: việc "hỏi lại" này CHỈ chạy trong `SagaRecoveryJob` (job
nền `@Scheduled`, không chiếm Tomcat thread của request thật), và
CHỈ chạy cho saga đã thực sự vượt ngưỡng "stuck" — không phải mọi
webhook bình thường, không giữ thread chờ kết quả ngay tại chỗ.
