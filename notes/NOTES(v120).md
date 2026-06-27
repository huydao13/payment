Trả về trạng thái thật hiện tại của transaction (PENDING/SUCCESS/FAILED),
đọc trực tiếp từ bảng `provider_transactions` — không phụ thuộc webhook.

2. **Reconciliation job ở `payment-service`** — `@Scheduled`, chạy
   định kỳ (ví dụ mỗi 1-5 phút tuỳ kết quả đo performance), query:
```sql
   SELECT * FROM payments
   WHERE status = 'PENDING' AND created_at < now() - interval 'X phút'
```
Với mỗi bản ghi tìm được, gọi `GET /api/provider/transactions/{id}`
để hỏi lại trạng thái thật, rồi cập nhật `Payment` + gọi
`orchestratorClient.notifyPaymentResult(...)` giống `handleWebhook()`
đang làm — tái dùng được gần hết logic cũ.

3. **TTL/expire cho idempotency key** — nếu sau X phút vẫn không có
   kết quả (cả webhook và reconciliation job đều không tìm ra), tự
   đánh `FAILED` với message rõ ràng "Timeout — không nhận được phản
   hồi từ provider", tránh kẹt vĩnh viễn.

**Tại sao cần đo performance trước khi code phần này:**
- Ngưỡng "bao lâu thì coi là treo" (`X phút` ở trên) không nên đoán —
  cần biết phân phối thời gian xử lý thật của provider (P50/P95/P99
  delay) để chọn ngưỡng hợp lý. Đặt quá ngắn → false positive (báo
  treo nhầm trong khi vẫn đang xử lý bình thường). Đặt quá dài →
  người dùng chờ lâu mới biết đơn hàng fail.
- Tần suất chạy job (`@Scheduled` mỗi bao lâu) ảnh hưởng tải DB —
  cần biết quy mô traffic thật trước khi chọn interval, tránh job
  quét quá thường xuyên gây tốn tài nguyên không cần thiết.
- Đo trước cũng giúp biết tỷ lệ thật của 2 trường hợp mồ côi này có
  đáng để đầu tư code phần này ngay không, hay tạm ưu tiên việc khác.
