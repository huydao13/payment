## TODO (khi scale lớn): Tối ưu SagaRecoveryJob cho số lượng saga stuck lớn

**Vấn đề tiềm ẩn:** Code hiện tại xử lý tuần tự (for loop), với
N saga stuck × thời gian mỗi GET — nếu N lớn (hàng trăm-nghìn), 1 vòng
job có thể chạy lâu hơn 30 giây (chu kỳ @Scheduled), gây chồng chéo
vòng chạy, và tạo tải dồn lên payment-service.

**Cách khắc phục khi cần:**
1. Giới hạn batch mỗi vòng job (ví dụ chỉ xử lý tối đa 50 saga/vòng,
   ưu tiên saga stuck lâu nhất trước — `LIMIT 50 ORDER BY updatedAt ASC`)
2. Gọi GET /status song song có giới hạn (ví dụ CompletableFuture +
   thread pool riêng, max 10 request đồng thời) thay vì tuần tự
3. Theo dõi thời gian chạy thật của mỗi vòng job (log start/end), nếu
   gần chạm 30 giây, cảnh báo cần tối ưu trước khi traffic tăng thêm
