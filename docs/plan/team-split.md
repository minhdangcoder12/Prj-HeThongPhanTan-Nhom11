# Phân công 3 thành viên – RideFlow

Căn cứ: đề bài `COURSE-PROJECT-SPECIFICATION.pdf` (topic DS03) và `implementation-plan.md` §11 (bảng đối chiếu đề). Phase 0 (khung codebase) **đã xong**.

**Thành viên:** A – Nguyễn Minh Đăng · B – Phạm Hải Long · C – Lê Nguyên Nhật Minh

**Mốc cứng:** thi giữa kỳ 16/11 · **nộp code + report qua GitHub 22/11/2026** · demo + vấn đáp 23/11 hoặc 30/11.

## Nguyên tắc chia

- **Chia theo luồng nghiệp vụ, không theo tầng.** Mỗi người sở hữu trọn vẹn service của mình (domain → API → event → test), **2 demo flow** để tự trình bày, và **các chương report** tương ứng (đề yêu cầu mỗi người giải thích được phần mình và cả kiến trúc chung).
- **Cân độ khó bằng điểm**: 1 = dễ (≤ nửa ngày), 2 = trung bình, 3 = khó, 5 = rất khó. Mỗi người **19 điểm** = 15 điểm phần lõi (đúng **1 phần 5 điểm**) + 4 điểm tính năng nâng cao mục 7.5 (đề yêu cầu làm đủ).
- Chỗ 2 người cùng đụng 1 service thì chia theo **package**, giao tiếp qua **port interface**.
- Bằng chứng đóng góp = **commit của chính mình trên GitHub** (đề bắt buộc). Không commit hộ, không squash mất tên tác giả.

## Tổng quan

| | **A – Nguyễn Minh Đăng**<br>Trip & Identity | **B – Phạm Hải Long**<br>Driver & Matching Saga | **C – Lê Nguyên Nhật Minh**<br>Reliability & Evaluation |
|---|---|---|---|
| Service sở hữu | user-service, trip-service (gồm tính cước), JWT ở gateway | driver-service, matching-service (saga) | payment-service, notification-service, Redis projection, CI, đo hiệu năng |
| Phần 5 điểm | Vòng đời chuyến + tính cước + outbox + assign idempotent | Saga orchestration | Payment + notification với inbox, retry/DLQ |
| Demo flow trình bày | **A** happy path · **E** (phía trip) | **B** 2 khách tranh 1 tài xế · **E** (compensation) | **C** tắt service giữa luồng → bật lại · **D** event trùng |
| Panel frontend | Passenger | Driver | Monitor + Kịch bản demo |
| Chương report riêng (§10.2 đề) | 4, 9, 12 | 7, 8, 10 | 11, 14, 15 |
| Chương chung | 1–3 | 5, 6 | 16–19 |
| Việc nộp bài | Kịch bản demo tổng, điều phối diễn tập | **Tổng hợp `report.pdf`** | **Làm `slides.pdf`** |
| Nâng cao 7.5 (4đ) | Service discovery (Consul) + dynamic pricing | Circuit breaker + chạy nhiều instance | Distributed tracing (Jaeger) + log tập trung (Loki/Grafana) |
| Tổng điểm | 19 | 19 | 19 |

Chương 13 (Implementation): mỗi người viết phần service của mình. Mỗi người: 3 chương riêng + phần chương 13 + 1 nhóm chương chung + 1 việc nộp bài.

---

## A – Nguyễn Minh Đăng: Trip & Identity (19đ)

| # | Việc | Điểm | Xong khi |
|---|---|---|---|
| A1 | **trip-service – vòng đời chuyến**: `Trip.transitionTo()` với bảng chuyển hợp lệ (sai → 409); **tính cước** trong domain khi tạo chuyến (giá mở cửa + đơn giá/km × khoảng cách haversine, lưu `fare`); `POST /api/trips` (`@Valid`, `Idempotency-Key`); accept/start/complete; ghi `trip_status_history`; phát `trip.*` qua `OutboxWriter` cùng transaction | 5 | Unit test đủ cặp chuyển + test tính cước; `flow-a.sh` qua A3 |
| A2 | `POST /internal/trips/{id}/assign-driver` idempotent (`idempotency_record`) + `trip.driver-assigned`; `trip.no-driver` khi saga báo hết ứng viên | 2 | Gọi 2 lần cùng key → assign 1 lần (test đồng thời CC2) |
| A3 | Consumer `payment.succeeded/failed` → `PAID`/`PAYMENT_FAILED`; bỏ qua event sai thứ tự (rule R3 out-of-order) | 1 | Gửi trùng / sai thứ tự không đổi trạng thái sai |
| A4 | **user-service**: đăng ký **có chọn role PASSENGER/DRIVER**, đăng nhập, BCrypt, JWT (secret từ `JWT_SECRET`), seed 2 passenger + 3 tài xế (`V3__seed_users.sql`, liên kết `drivers.user_id`) | 3 | Login trả token; sai mật khẩu → 401 |
| A5 | Gateway kiểm JWT cho `/api/**` (trừ `/api/auth/**`, `/api/monitor/**`), chuyển `X-User-Id`/`X-User-Role`; service kiểm lại role ở endpoint ghi | 2 | Test bảo mật SEC1 xanh |
| A6 | Chaos Flow E phía trip: `PUT /api/chaos/trip/fail-assign` (`@Profile("demo")`) | 1 | Bật cờ → saga của B kết thúc COMPENSATED |
| A7 | Passenger panel: login + gắn token trong `api.js`; kịch bản demo Flow A trong README | 1 | Chạy hết Flow A chỉ bằng UI |
| A8 | **7.5 Service discovery**: thêm Consul vào compose; `spring-cloud-starter-consul-discovery` trong `rideflow-common` và gateway; route gateway dùng `lb://<service>` thay URL cố định; `/api/monitor/health` lấy danh sách từ `DiscoveryClient` | 2 | Consul UI (:8500) thấy đủ service; tắt 1 service → Consul báo critical; gateway vẫn route khi service đổi IP |
| A9 | **7.5 Dynamic pricing**: hệ số giá = số chuyến đang chờ ghép / số tài xế rảnh trong bán kính 3 km, kẹp 1.0–2.0; supply lấy từ `GET /internal/matching/supply?lat&lng` (C cung cấp); lưu `surge_multiplier` vào trip (migration mới); hiện trên Passenger panel | 2 | Test: 1 tài xế + 3 khách chờ → hệ số > 1; nhiều tài xế → 1.0 |

## B – Phạm Hải Long: Driver & Matching Saga (19đ)

| # | Việc | Điểm | Xong khi |
|---|---|---|---|
| B1 | **matching-service – Saga orchestrator**: consume `trip.created` (inbox) → `matching_sagas` STARTED → ứng viên qua port `DriverLocator` (C cài Redis) → reserve → assign → COMPLETED; ghi `matching_attempts`; lưu trạng thái **trước** mỗi bước; gọi `/internal` với timeout + retry có giới hạn (rule R4); phát `matching.saga-changed` | 5 | Saga đủ bước trên Monitor; restart matching giữa chừng không mất trạng thái |
| B2 | **Compensation**: assign lỗi vĩnh viễn → COMPENSATING → release → COMPENSATED; reserve 409 → ứng viên kế; hết ứng viên → FAILED + báo trip | 2 | `tests/e2e/flow-e.sh` xanh |
| B3 | **driver-service – atomic reservation**: `POST /internal/drivers/{id}/reserve` bằng 1 câu `UPDATE ... WHERE status='AVAILABLE'`, `release`; idempotent theo `Idempotency-Key`; `driver.reserved/released` | 3 | Test Testcontainers 20 thread → đúng 1 thắng (CC1) |
| B4 | driver-service: `PUT availability` / `PUT location` + `driver.availability-changed` / `driver.location-updated` | 2 | `flow-a.sh` qua A1, A2 |
| B5 | Sweeper `@Scheduled` giải phóng reservation quá `reserved_until` (lease – đề 4.4) | 1 | Tài xế kẹt RESERVED tự về AVAILABLE |
| B6 | Reject flow: consume `trip.driver-rejected` → release + thử tài xế khác | 1 | Test F9 xanh |
| B7 | Driver panel hoàn chỉnh; viết `tests/e2e/flow-e.sh` **trước** khi làm B2 | 1 | Tài xế thao tác hết vòng đời bằng UI |
| B8 | **7.5 Circuit breaker**: Resilience4j (`spring-cloud-starter-circuitbreaker-resilience4j`) bọc client matching → driver/trip; mạch mở → saga fail nhanh → compensation/FAILED thay vì chờ; lộ trạng thái qua `/actuator/circuitbreakers` | 2 | Test FT5: kill driver-service → mạch OPEN sau vài lần lỗi, saga FAILED trong < 2s; bật lại → HALF_OPEN → CLOSED |
| B9 | **7.5 Multi-instance scaling**: bỏ port host cố định của service (gateway LB qua A8); `scripts/scale.sh` chạy `--scale matching-service=2 --scale trip-service=2`; chứng minh saga/reserve/outbox đúng khi 2 instance cùng chạy (SKIP LOCKED, unique `trip_id`, competing consumers) | 2 | Flow A + Flow B vẫn xanh với 2 instance; thí nghiệm P2 (1 vs 2 instance) |

## C – Lê Nguyên Nhật Minh: Reliability & Evaluation (19đ)

| # | Việc | Điểm | Xong khi |
|---|---|---|---|
| C1 | **payment-service**: consume `trip.completed` (inbox) → `payments` (unique `trip_id`) → giả lập thanh toán (tỉ lệ lỗi cấu hình) → `payment.*` qua outbox | 3 | Trùng `trip.completed` → 1 payment; `flow-restart.sh` xanh |
| C2 | **notification-service**: gửi cho **cả passenger và driver** theo từng event; chaos `PUT /api/chaos/notification/fail-rate` → retry 2s/5s/10s → DLQ; `notification.sent/failed` | 2 | fail-rate=1 → message vào `.dlq`, Monitor hiện DLQ |
| C3 | **DLQ reprocess** + đếm DLQ trên Monitor | 2 | Tắt lỗi → reprocess → SENT, DLQ rỗng |
| C4 | **Redis GEO projection** trong matching (`adapter/in/messaging/projection`, `adapter/out/redis`): `GEOADD`/`ZREM` theo event driver; cài `DriverLocator` bằng `GEOSEARCH` | 3 | Tài xế gần nhất được chọn (test F5) |
| C5 | Eval phân tán: `flow-b.sh` (2 khách song song 1 tài xế), `flow-c.sh` (retry/DLQ); chạy & ghi kết quả `flow-restart.sh`, Flow D | 3 | Script chạy lại được, kết quả vào `docs/` |
| C6 | CI GitHub Actions: `./mvnw verify` + `npm run build` | 1 | PR đỏ khi test/ArchUnit fail |
| C8 | **7.5 Distributed tracing**: Micrometer Tracing + OpenTelemetry (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`) trong `rideflow-common` + gateway; Jaeger trong compose; bật observation cho `RabbitTemplate` và listener để trace đi xuyên REST **và** queue; log có `traceId` | 2 | Jaeger UI (:16686) hiện 1 trace đặt xe đi qua gateway → trip → RabbitMQ → matching → driver |
| C9 | **7.5 Log tập trung**: Loki + Promtail (đọc log container Docker) + Grafana, gom vào compose profile `observability`; dashboard Grafana lọc log theo service / correlationId / traceId, link sang Jaeger. Cài `GET /internal/matching/supply` (đếm tài xế rảnh từ Redis) cho A9 | 2 | Grafana (:3000) tìm 1 correlationId thấy log của mọi service liên quan |
| C7 | **Thí nghiệm hiệu năng** `scripts/load.sh`: N yêu cầu đặt xe đồng thời, đo latency p50/p95, throughput, thời gian ghép, recovery time; bảng/biểu đồ cho chương 15 | 1 | Số liệu + cấu hình máy ghi trong report |

---

## Ma trận kiểm thử (đề mục 9 – tối thiểu 8 / 2 / 2 / 2 / 1)

| Mã | Loại | Nội dung | Chủ | Nằm ở |
|---|---|---|---|---|
| F1 | Chức năng | Đăng ký có role + đăng nhập | A | user-service test |
| F2 | Chức năng | Tạo chuyến tính đúng cước | A | trip domain test |
| F3 | Chức năng | Vòng đời accept → start → complete, chuyển sai → 409 | A | trip test + `flow-a.sh` |
| F4 | Chức năng | Tài xế online/offline + cập nhật vị trí | B | driver test + `flow-a.sh` |
| F5 | Chức năng | Matching chọn tài xế gần nhất | C | matching test (Redis Testcontainers) |
| F6 | Chức năng | Saga gán tài xế end-to-end | B | `flow-a.sh` A4 |
| F7 | Chức năng | Thanh toán sau khi hoàn thành → PAID | C | `flow-a.sh` A6 |
| F8 | Chức năng | Notification cho passenger và driver | C | notification test |
| F9 | Chức năng | Tài xế từ chối → gán tài xế khác | B | matching test |
| CC1 | Đồng thời | 2 khách cùng lúc tranh 1 tài xế → đúng 1 thắng | B (unit 20 thread) + C (`flow-b.sh`) | driver test + e2e |
| CC2 | Đồng thời | 2 lệnh assign trùng `Idempotency-Key` song song → 1 hiệu ứng | A | trip test |
| FT1 | Lỗi/khôi phục | Kill payment-service giữa luồng → bật lại → PAID | C | `flow-restart.sh` |
| FT2 | Lỗi/khôi phục | Assign lỗi → compensation → tài xế AVAILABLE | B | `flow-e.sh` |
| FT3 | Lỗi/khôi phục | Notification lỗi → retry → DLQ → reprocess | C | `flow-c.sh` |
| FT4 | Lỗi/khôi phục | Event trùng (replay cùng eventId) → 1 hiệu ứng | C | `regression.sh` R7–R8 ✅ |
| SEC1 | Bảo mật | Không token → 401; passenger gọi hành động tài xế → 403 | A | gateway/trip test |
| SEC2 | Dữ liệu sai | Toạ độ sai/thiếu → 400; body > 64KB → 413 | A | trip test + `regression.sh` R9 ✅ |
| P1 | Hiệu năng | Tải đồng thời + recovery time | C | `scripts/load.sh` |
| P2 | Hiệu năng | Throughput 1 instance vs 2 instance (7.5 scaling) | B | `scripts/load.sh` + `scripts/scale.sh` |
| FT5 | Lỗi/khôi phục | driver-service chết → circuit breaker mở → saga FAILED nhanh → bật lại tự đóng mạch | B | matching test + Monitor |

## Phụ thuộc giữa các người – chốt trong 2 ngày đầu

| Hợp đồng | Cung cấp → dùng | Chốt ở |
|---|---|---|
| Payload `trip.*` | A → B, C | `contracts/asyncapi/asyncapi.yaml` |
| `POST /internal/trips/{id}/assign-driver` | A → B | tạo `contracts/openapi/internal-api.yaml` |
| `POST /internal/drivers/{id}/reserve`, `/release` | B → B | `internal-api.yaml` |
| Port `DriverLocator.nearestAvailable(lat, lng, limit, excluded)` | B định nghĩa → C cài | `matching-service/.../application/port/out/` |
| Payload `driver.*` | B → C | asyncapi |
| Payload `payment.*` | C → A | asyncapi |
| `GET /internal/matching/supply?lat&lng` (số tài xế rảnh) | C → A (dynamic pricing) | `internal-api.yaml` |
| Consul + `lb://` (A8) | A → B (B9 cần để chạy nhiều instance) | `deploy/docker-compose.yml` |

Bên kia chưa xong thì dùng **stub** (Mockito, hoặc bắn event tay qua RabbitMQ UI / nút "Gửi event test").

## Lịch (3 tuần đến hạn 22/11)

| Ngày | A – Nguyễn Minh Đăng | B – Phạm Hải Long | C – Lê Nguyên Nhật Minh | Mốc chung |
|---|---|---|---|---|
| 02–04/11 | Chốt contract; A4, A8 | Chốt contract; B4 | Chốt contract; C6, C8, bắt đầu C4 | Đăng ký nhóm với giảng viên; tạo repo GitHub, mời giảng viên |
| 05–08/11 | A1, A2, A5 | B3, B1 | C4, C1 | |
| 09–11/11 | A3, A7 | B1 xong, B7 | C2, C9 | **11/11: `flow-a.sh` xanh** |
| 12–15/11 | A6, A9 | B2, B5, B6, B8 | C3, C5 | **15/11: flow B, C, E, restart xanh** |
| 16/11 | — | — | — | Thi giữa kỳ |
| 17–19/11 | Test SEC1/SEC2/CC2; viết chương 1–3, 4, 9, 12 + phần ch.13 | B9 (17–18/11); test CC1/F9/FT5/P2; viết chương 5, 6, 7, 8, 10 + phần ch.13 | C7 đo hiệu năng; viết chương 11, 14, 15, 16–19 + phần ch.13 | Đóng băng tính năng 18/11 |
| 20–21/11 | Kịch bản demo tổng; điều phối diễn tập | Tổng hợp `docs/report.pdf`; diễn tập | Làm `docs/slides.pdf`; chạy lại README từ đầu trên máy sạch; diễn tập | **21/11: tag `v1.0.0`** |
| 22/11 | | | | **Hạn nộp** |

## Quy trình làm việc

- Mỗi người 1 branch cho 1 nhóm task cùng mảng, tên đọc là hiểu: `feat/<service-hoặc-mảng>-<việc-chính>` (vd `feat/login-jwt-passenger-ui` = A4+A5+A7), mỗi task ≥ 1 commit riêng, merge xong xoá branch; PR cần **1 người khác review**, xoay vòng A→B→C→A để ai cũng hiểu phần người khác (giảng viên hỏi chéo).
- Chỉ merge khi `./mvnw verify` xanh và `tests/e2e/regression.sh` 9/9. Đổi API/event → sửa contract cùng PR. Đổi schema → migration mới.
- Tuân thủ [`CONTRIBUTING.md`](../../CONTRIBUTING.md) (quy tắc chung, checklist PR). Nếu dùng Claude Code: `/ecc:plan` trước task ≥ 3 điểm, `ecc:tdd-workflow` khi code, `/ecc:code-review` trước PR. Chia unit, chọn model, review theo [`agentic-workflow.md`](agentic-workflow.md).
- Đề cho phép dùng AI nhưng **nhóm phải hiểu và chịu trách nhiệm** – mỗi người phải giải thích được từng dòng trong PR của mình khi vấn đáp.
