# Messaging, concurrency & idempotency rules

## R3 – Event
- Exchange topic `rideflow.events`; dead-letter exchange `rideflow.dlx`. Routing key = `eventType`.
- Mọi event là `com.rideflow.common.event.EventEnvelope` (eventId, eventType, eventVersion, correlationId, causationId, aggregateId, producer, occurredAt, payload).
- Tên event lowercase dotted: `trip.created`, `driver.reserved`... Tên là hợp đồng: thêm/đổi phải sửa `contracts/asyncapi/asyncapi.yaml` trong cùng PR.
- Publish: gọi `OutboxWriter.append(envelope)` TRONG cùng transaction với thay đổi nghiệp vụ. CẤM `rabbitTemplate.send` trực tiếp trong use case (dual write). `OutboxRelay` tự đẩy lên RabbitMQ.
- Consume: mỗi consumer có queue riêng, khai báo bằng `RideflowMessaging.consumerQueue("<svc>.<purpose>.q", "routing.key"...)` (tự tạo DLQ `<queue>.dlq`).
- Listener gọi `InboxGuard.firstTime(eventId, consumer, eventType)` trong transaction nghiệp vụ; trả `false` → bỏ qua (duplicate).
- Retry bounded 2s → 5s → 10s rồi vào DLQ (cấu hình chung `rideflow-common.yml`). CẤM retry vô hạn, CẤM nuốt exception để "đỡ lỗi".
- Không tuyên bố RabbitMQ exactly-once: at-least-once + inbox = exactly-once business effect.
- **Out-of-order** (spec 4.2): consumer kiểm trạng thái hiện tại trước khi áp dụng event. Event không hợp lệ với trạng thái (vd `payment.succeeded` khi trip chưa COMPLETED, `driver.location-updated` cũ hơn `updated_at` đang lưu) → log WARN và bỏ qua, không ném lỗi (ném lỗi sẽ retry vô ích rồi vào DLQ).

## R4 – Concurrency & idempotency
- "1 tài xế không bị giữ cho 2 chuyến" chỉ được bảo đảm bằng atomic SQL ở driver-service:
  `UPDATE drivers SET status='RESERVED', current_trip_id=? ... WHERE id=? AND status='AVAILABLE'` → affected rows 1 = thắng, 0 = thua.
- CẤM dùng `synchronized`, lock in-memory, hay check-then-update làm cơ chế chính.
- Lệnh có thể retry (reserve, release, assign, payment, consume event) phải idempotent. REST internal nhận header `Idempotency-Key`, lưu ở bảng `idempotency_record`.
- ID: UUID. Thời gian: `Instant` (UTC), cột `TIMESTAMPTZ`.
- Gọi REST `/internal/**` luôn đặt timeout (connect 1s, read 3s) và retry có giới hạn (tối đa 3 lần, cùng `Idempotency-Key`). Hết retry = lỗi vĩnh viễn → saga compensation. CẤM chờ vô hạn.
