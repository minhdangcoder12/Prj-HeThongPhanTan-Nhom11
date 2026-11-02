# Plan: RideFlow – Ride-Hailing Microservices (mô phỏng Grab/Uber)

**Nguồn tham khảo**: https://github.com/hoangmelinh/DS03_Ride-hailing-microservices-system-simulating-Grab-Uber (sau đây gọi là `ref`)
**Phạm vi plan này**: Phase 0 – dựng khung codebase + rules. Các phase sau chỉ liệt kê ở mức roadmap.
**Complexity**: Large (toàn dự án) / Medium (Phase 0)
**Trạng thái**: Phase 0 ĐÃ XONG (02/11). Đối chiếu với đề bài ở §11, phân công ở `team-split.md`. Layout repo đã đổi theo đề mục 10.1 (`src/ deploy/ tests/ scripts/ docs/`) – các đường dẫn ở §5 là bản gốc trước khi đổi.

---

## 1. Nhắc lại yêu cầu

- Hệ thống gọi xe phân tán: hành khách đặt chuyến → ghép tài xế → tài xế nhận → chạy → hoàn thành → thanh toán → thông báo.
- Kiến trúc microservices + event-driven, thể hiện được các kỹ thuật phân tán: Saga, atomic reservation, Outbox/Inbox, idempotency, bounded retry + DLQ, correlation ID.
- Web client **tối giản** React + Vite: chỉ để *kích hoạt* luồng nghiệp vụ và *quan sát* trạng thái hệ thống khi demo/vấn đáp. Không cần đẹp, cần **nhìn thấy được hệ thống phân tán đang làm gì**.
- Giữ stack của `ref` (Java 21, Spring Boot 3.3, RabbitMQ, PostgreSQL, Redis) để tận dụng tài liệu kiến trúc đã có.

## 2. Phân tích repo `ref`

### Điểm mạnh (giữ lại)
| Hạng mục | Nhận xét |
|---|---|
| `docs/architecture/architecture.md` (1.8k dòng) | Rất đầy đủ: bounded context, saga state, event naming, topology RabbitMQ, 5 demo flow A–E. Dùng làm source of truth. |
| Flyway migrations 6 service | Schema hợp lý: `outbox_event`, `inbox_event`, `idempotency_record`, `dead_letter_messages`, `matching_sagas`. Port gần như nguyên vẹn. |
| Atomic reservation | `UPDATE ... WHERE status='AVAILABLE'` + kiểm tra affected rows ở driver-service – đúng cách. |
| `src/libs/goride-common` | `EventEnvelope`, `ApiResponse`, exception base – đúng hướng (chỉ chứa technical). |
| Docker Compose hạ tầng | Postgres (1 instance, nhiều DB), RabbitMQ management, Redis, có healthcheck. |

### Vấn đề / khoảng trống (cần cải thiện)
| # | Vấn đề | Ảnh hưởng | Cải thiện đề xuất |
|---|---|---|---|
| G1 | Code mới chỉ có entity + repository. **Không có** application layer, controller, consumer, publisher, client. Gateway `routes: []`. | Chưa chạy được luồng nào. | Phase 0 dựng đủ khung các layer + route; Phase 1+ lấp logic theo flow. |
| G2 | **Lệch layout**: driver-service dùng `adapter/out/persistence` (đúng doc §8), 5 service còn lại dùng `infrastructure/persistence` (trái doc). | Vi phạm chính rule của dự án, ArchUnit khó viết chung. | Thống nhất 1 layout `domain / application / adapter / infrastructure` cho mọi service + 1 bộ ArchUnit rule dùng chung. |
| G3 | Outbox/Inbox/DLQ/Idempotency entity + repository **copy-paste ở 5–6 service**, mỗi bản lệch nhau (enum `DlqMessageStatus`, `OutboxStatus` lặp lại). | Sửa 1 lỗi phải sửa 6 nơi. | Đưa phần *technical* (OutboxPublisher, InboxGuard, IdempotencyFilter, CorrelationId filter) vào `src/libs/goride-common` – được phép theo rule "shared lib chỉ chứa technical". Bảng vẫn nằm trong DB riêng mỗi service. |
| G4 | `contracts/openapi`, `contracts/asyncapi` chỉ có README. | Không có hợp đồng để frontend/service bám. | Tạo `asyncapi.yaml` (đủ danh sách event + envelope) và `openapi` tối thiểu cho endpoint public mà frontend gọi. |
| G5 | `src/demo-client` rỗng. **Không có cơ chế cho System Monitor xem event** – doc yêu cầu nhưng không nói lấy từ đâu. | Phần quan trọng nhất khi vấn đáp không có. | **Event tap ở api-gateway**: queue `monitor.tap.q` (non-durable, auto-delete) bind `#` vào `goride.events`, giữ ring buffer 200 event, đẩy ra frontend qua **SSE** `GET /api/monitor/events`. Gateway không bị tắt khi demo nên monitor không chết theo service (khác với đặt ở notification-service, sẽ chết ở Flow C). |
| G6 | Flow D (duplicate event) và Flow E (saga compensation) **không có cách kích hoạt** từ UI. | Phải sửa code/DB thủ công khi demo. | Thêm **Chaos panel** (chỉ bật ở profile `demo`): `POST /api/chaos/trip/fail-assign` (Flow E), `POST /api/chaos/notification/fail-rate` (Flow C không cần tắt container), `POST /api/monitor/replay/{eventId}` – gateway publish lại event đã tap với **cùng eventId** (Flow D). |
| G7 | Máy dev không có `mvn` (đã kiểm tra), `ref` không có Maven Wrapper. | Không build được nếu thiếu Maven. | Thêm `mvnw` + build trong Docker multi-stage (1 `Dockerfile` dùng chung, `ARG SERVICE`). |
| G8 | 7 JVM + Postgres + RabbitMQ + Redis (+ Prometheus/Grafana/Jaeger trong `.env`) khá nặng cho laptop. | Demo dễ treo máy. | Giới hạn `-Xmx256m`/service; bỏ Grafana/Jaeger khỏi Phase 0 (correlation ID trong log + SSE monitor đủ để demo). Thêm lại ở phase observability nếu còn thời gian. |
| G9 | Reservation có `expires_at` nhưng không có cơ chế giải phóng reservation quá hạn khi matching-service chết giữa saga. | Tài xế kẹt `RESERVED` mãi. | Scheduled sweeper ở driver-service release reservation hết hạn (phát `driver.released`). |
| G10 | Thiếu luồng **tài xế từ chối** (Driver panel có "reject" nhưng flow không mô tả). | UI có nút nhưng không có hành vi. | Reject → trip quay lại `MATCHING`, phát `trip.driver-rejected` → matching thử ứng viên kế tiếp (loại tài xế đã từ chối). |

## 3. Kiến trúc mục tiêu

```text
React+Vite demo-client (5173)
        │  REST + SSE
        ▼
api-gateway (8080)  ── JWT check, X-Correlation-Id, routes, event tap (SSE), chaos/replay
        │
 ┌──────┼──────────┬────────────┬─────────────┬───────────────┐
 ▼      ▼          ▼            ▼             ▼               ▼
user   driver     trip       matching      payment      notification
8081   8082       8083       8084          8085         8086
 │      │          │            │  Redis GEO   │             │
 PG     PG         PG           PG            PG            PG     (1 Postgres, 6 DB)
 └──────┴────── RabbitMQ topic exchange `rideflow.events` (+ DLX `rideflow.dlx`) ──────┘
```

Giao tiếp:
- **Sync REST (internal)**: matching → driver `POST /internal/drivers/{id}/reserve|release`, matching → trip `POST /internal/trips/{id}/assign-driver` (có `Idempotency-Key`).
- **Async event**: theo bảng event của `ref` §19, bổ sung `trip.driver-rejected`.

## 4. Rules (sẽ ghi vào `CLAUDE.md` + `.claude/rules/`)

> Rút gọn từ `ref/.agents/AGENTS.md`, sửa các điểm G2/G3, thêm rule frontend & demo.

**R1 – Ranh giới service**
- Mỗi service một DB, không truy cập DB/repository của service khác, không FK chéo DB.
- Liên service chỉ qua REST (`/internal/**`) hoặc event. Gateway chỉ expose `/api/**`; `/internal/**` không được route ra ngoài.
- `src/libs/rideflow-common` chỉ chứa technical (envelope, outbox/inbox, correlation, error format). Cấm domain model dùng chung.

**R2 – Layout trong mỗi service (bắt buộc, ArchUnit kiểm tra)**
```text
com.rideflow.<svc>.domain         // POJO thuần, không Spring/JPA/AMQP/HTTP
com.rideflow.<svc>.application    // use case + port in/out, chỉ phụ thuộc domain
com.rideflow.<svc>.adapter.in     // web controller, rabbit listener (mỏng)
com.rideflow.<svc>.adapter.out    // persistence (JPA entity ≠ domain), messaging, http client
com.rideflow.<svc>.infrastructure // config, security
```

**R3 – Messaging**
- Mọi event dùng `EventEnvelope` (eventId, eventType, eventVersion, correlationId, causationId, aggregateId, producer, occurredAt, payload).
- Tên event: lowercase dotted (`trip.created`); là hợp đồng – đổi tên phải sửa `contracts/asyncapi`.
- Publish event quan trọng qua **Transactional Outbox**; consumer đi qua **Inbox** (dedupe theo `eventId` + consumer).
- Retry **bounded** 2s → 5s → 10s rồi DLQ. Cấm retry vô hạn.

**R4 – Đồng thời & idempotency**
- Reservation tài xế chỉ bằng atomic SQL ở driver-service. Cấm `synchronized`/lock in-memory làm cơ chế chính.
- Mọi lệnh có thể retry (reserve, release, assign, payment, consume) phải idempotent; REST internal dùng header `Idempotency-Key`.
- ID là UUID, thời gian là UTC `Instant`.

**R5 – Saga**
- matching-service điều phối, lưu trạng thái saga vào `matching_sagas` trước mỗi bước.
- Reserve thành công nhưng assign thất bại vĩnh viễn → COMPENSATING → release → COMPENSATED.

**R6 – Observability tối thiểu**
- Header `X-Correlation-Id` sinh ở gateway nếu thiếu, truyền qua REST + envelope, đưa vào MDC log. Cấm `System.out`.
- Mỗi service có `/actuator/health`.

**R7 – Frontend (demo-client)**
- Mỏng: không chứa business rule, không tự suy trạng thái – chỉ hiển thị trạng thái server trả về / event SSE.
- Chỉ gọi `/api/**` qua gateway (Vite proxy), không gọi thẳng service.
- Không thêm UI library/state library; React state + `fetch` + `EventSource` là đủ.

**R8 – Bảo mật**
- Không hard-code secret; mọi credential qua env (`.env` bị gitignore, có `.env.example`).
- JWT kiểm tra ở gateway, role kiểm tra lại ở service. Chaos/replay endpoint chỉ tồn tại khi `SPRING_PROFILES_ACTIVE=demo`.

**R9 – DB & test**
- Schema chỉ qua Flyway, `ddl-auto=validate`.
- Thay đổi business không trivial phải có test; các kịch bản phân tán (concurrency, duplicate, retry, compensation) phải có test tương ứng.

## 5. Phase 0 – Files sẽ tạo (sau khi xác nhận)

| Đường dẫn | Nội dung |
|---|---|
| `CLAUDE.md`, `.claude/rules/*.md` | Rules R1–R9 ở trên |
| `pom.xml`, `mvnw*`, `.mvn/` | Parent POM Java 21 / Boot 3.3.x / Cloud 2023.0.x, Maven Wrapper |
| `src/libs/rideflow-common` | `EventEnvelope`, `ApiResponse`, exceptions (port từ `ref`), `CorrelationIdFilter`, outbox publisher + inbox guard (khung) |
| `services/<6 service>` | Khung đủ 4 layer, `Application`, `application.yml` (env-based), Flyway V1 (port từ `ref`), 1 controller ping, 1 ArchUnit test |
| `services/api-gateway` | Routes `/api/**` → service, correlation filter, event tap SSE, chaos/replay (profile demo) |
| `contracts/asyncapi/asyncapi.yaml`, `contracts/openapi/*.yaml` | Hợp đồng event + REST public |
| `docker-compose.yml` (gốc repo), `Dockerfile` | Hạ tầng + 7 service, 1 Dockerfile multi-stage dùng chung |
| `src/demo-client` | Vite + React (JS), 4 panel: Passenger, Driver, System Monitor (SSE + bảng trip/driver/saga), Chaos |
| `.env.example`, `.gitignore`, `README.md` | |

## 6. Tasks Phase 0

1. **Repo & build** – parent POM, wrapper, libs. *Validate*: `./mvnw -q -DskipTests package` (hoặc build Docker nếu wrapper không tải được).
2. **Khung 6 service + gateway** theo R2. *Validate*: `./mvnw verify` – ArchUnit pass.
3. **Compose** hạ tầng + service. *Validate*: `docker compose up -d` → mọi `/actuator/health` = UP.
4. **Gateway event tap + SSE**. *Validate*: publish 1 message test vào `rideflow.events` → thấy trên `curl -N localhost:8080/api/monitor/events`.
5. **Contracts** asyncapi/openapi.
6. **Frontend** 4 panel gọi được health + nhận SSE. *Validate*: `npm run build`; mở 5173 thấy event test.
7. Rules + README.

## 7. Roadmap các phase sau (mỗi phase = 1 demo flow chạy được)

| Phase | Mục tiêu | Flow |
|---|---|---|
| 1 | User login JWT, driver online/location, trip create → matching saga → assign → accept/start/complete | A (đến completed) |
| 2 | Outbox/Inbox thật, payment + notification async | A (đủ) + D |
| 3 | Concurrency test 2 passenger/1 driver (Testcontainers + script) | B |
| 4 | Chaos fail-assign + compensation, reservation sweeper (G9), reject flow (G10) | E |
| 5 | Retry 2s/5s/10s + DLQ + reprocess từ UI | C |
| 6 | (tùy chọn) Prometheus/Grafana, k6 load test | – |

## 8. Risks

| Risk | Mức | Giảm thiểu |
|---|---|---|
| RAM khi chạy 7 JVM + hạ tầng | HIGH | `-Xmx256m`, compose profile để chỉ bật service cần cho từng flow |
| Không có Maven local | MEDIUM | Maven Wrapper; fallback build trong image `maven:3.9-eclipse-temurin-21` |
| Retry bằng Spring Retry trong listener chặn consumer lúc backoff | MEDIUM | Chấp nhận cho demo (ghi `shortcut:` comment); nâng lên retry queue TTL+DLX nếu cần throughput |
| Event tap ở gateway là điểm quan sát duy nhất | LOW | Queue non-durable: gateway restart chỉ mất lịch sử monitor, không ảnh hưởng nghiệp vụ |
| Lệch tài liệu `ref` vs plan này | LOW | Bảng §2 ghi rõ chỗ khác `ref`; port `architecture.md` kèm ghi chú |

## 9. Skill ECC dùng ở các bước

- Phase 0: `ecc:springboot-patterns`, `ecc:hexagonal-architecture`, `ecc:docker-patterns`, `ecc:vite-patterns`, `ecc:api-design`.
- Phase 1+: `ecc:tdd-workflow` / `ecc:springboot-tdd`, `ecc:database-migrations`, `ecc:e2e-testing`.
- Sau mỗi phase: `ecc:code-review` (hoặc agent `ecc:java-reviewer`), `ecc:security-review` trước khi demo.

## 10. Acceptance Phase 0
- [x] `./mvnw verify` xanh (gồm ArchUnit)
- [x] `docker compose up` → 7 service healthy
- [x] Frontend hiển thị health 7 service + nhận event test qua SSE
- [x] Rules có trong `CLAUDE.md`, contracts có file yaml

## 11. Đối chiếu đề bài (COURSE-PROJECT-SPECIFICATION, topic DS03)

✅ = đã có trong code · 🔲 = task (mã trong `team-split.md`)

| Mục đề | Yêu cầu | Đáp ứng bằng | Trạng thái |
|---|---|---|---|
| 4.1 | ≥ 3 tiến trình độc lập, qua mạng, định danh riêng, log có node/thời gian/loại event/kết quả | 10 container; log `[service,correlationId]` + timestamp; event có `producer`, `eventType` | ✅ |
| 4.2 | Naming & service discovery | Docker DNS (tên service) + cấu hình `*.services.*` ở gateway; ID = UUID | ✅ (trình bày ở report ch.9) |
| 4.2 | Phát hiện online/offline | `/actuator/health` + `/api/monitor/health`; tài xế: lease `reserved_until` | ✅ / 🔲 B5 |
| 4.2 | Timeout, ack, retry | RabbitMQ ack + retry 2s/5s/10s → DLQ ✅; timeout REST internal (rule R4) | ✅ / 🔲 B1 |
| 4.2 | Message trùng / sai thứ tự | Inbox (`InboxGuard`) ✅; kiểm trạng thái trước khi áp dụng (rule R3) | ✅ / 🔲 A3 |
| 4.3 | Kịch bản đồng thời + mô hình nhất quán + test | 2 khách tranh 1 tài xế: atomic SQL (mạnh trong driver DB), eventual giữa các service | 🔲 B3, CC1 |
| 4.4 | ≥ 2 kịch bản lỗi | Kill service giữa luồng (`flow-restart.sh`), notification lỗi → DLQ, assign lỗi → compensation, event trùng ✅ | 🔲 C1, C2, B2 |
| 4.5 | Xác thực, phân quyền, validate, không lưu password thô, secret qua env, **giới hạn kích thước message**, log | JWT + BCrypt + `@Valid` 🔲 A4/A5; `.env` ✅; `RequestSize` 64KB ✅ (R9) | ✅ / 🔲 |
| 7.2 | ≥ 4 service nghiệp vụ, DB riêng | 6 service, 6 database | ✅ |
| 7.3 | Đăng ký + role, vị trí tài xế, đặt xe, ghép, accept/reject, không gán 2 chuyến, vòng đời, **tính cước + thanh toán**, thông báo | A1 (cước), A4, B3, B4, B6, C1, C2 | 🔲 |
| 7.3 | REST, message queue, event ID/idempotency key, retry, dedup, DLQ, correlation ID, Saga | REST internal, RabbitMQ, `EventEnvelope`, inbox, DLQ, `X-Correlation-Id` ✅; Saga 🔲 B1/B2 | ✅ / 🔲 |
| 7.4 | Start service độc lập | `bash scripts/compose.sh up -d <svc>` | ✅ |
| 7.4 | Tắt 1 service giữa luồng → retry/queue/compensation → bật lại xử lý tiếp | `scripts/fault.sh kill/start` + `tests/e2e/flow-restart.sh` (baseline FAIL ở S1) | ✅ script / 🔲 C1 |
| 7.5 | Nâng cao – **làm đủ cả 10 mục** | API Gateway ✅, Outbox ✅, Docker Compose ✅, dashboard Monitor ✅; geospatial 🔲 C4; service discovery 🔲 A8; dynamic pricing 🔲 A9; circuit breaker 🔲 B8; multi-instance 🔲 B9; distributed tracing 🔲 C8; log tập trung 🔲 C9 | 🔲 |
| 9 | Test 8 / 2 / 2 / 2 / 1 | Ma trận trong `team-split.md` | 🔲 |
| 10.1 | `README src/ deploy/ tests/ scripts/ docs/ .env.example`, bảng phân công, commit từng người, tag `v1.0.0`, mời giảng viên | Layout ✅; bảng phân công trong README ✅; git/tag 🔲 | |
| 10.2 | `docs/report.pdf` 20–30 trang, `slides.pdf` | Phân chương theo người trong `team-split.md` | 🔲 |
| 13 | Ghi nguồn, chịu trách nhiệm phần dùng AI | Mục "Nguồn tham khảo & AI" trong README | ✅ |

**Mốc thời gian:** giữa kỳ 16/11 · **nộp 22/11/2026** · demo 23/11 hoặc 30/11. Roadmap §7 được nén vào lịch 3 tuần trong `team-split.md`.
