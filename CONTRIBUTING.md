# Quy tắc làm việc chung – RideFlow

Áp dụng cho **mọi thành viên**, dùng công cụ gì cũng vậy (IntelliJ, VS Code, Claude, Copilot, Cursor…). PR vi phạm quy tắc ở đây sẽ không được merge.

Đọc kèm: [`README.md`](README.md) (chạy dự án) · [`docs/plan/team-split.md`](docs/plan/team-split.md) (ai làm gì, lịch) · contracts trong [`contracts/`](contracts).

Khi tài liệu mâu thuẫn, ưu tiên: **file này** → contracts (`contracts/asyncapi/asyncapi.yaml`, `contracts/openapi/public-api.yaml`) → `docs/plan/*`.

---

## 1. Cài đặt & lệnh hằng ngày

Yêu cầu: Docker Desktop, Git Bash (Windows) hoặc bash, Node 18+. JDK 21+ chỉ cần khi chạy service ngoài Docker. Không cần cài Maven (dùng `mvnw`).

| Việc | Lệnh |
|---|---|
| Lần đầu | `cp .env.example .env` (đổi mật khẩu trong `.env`, **không commit file này**) |
| Chạy cả hệ thống | `bash scripts/start.sh` (build + chờ mọi service UP) |
| Chạy lại 1 service sau khi sửa | `bash scripts/start.sh trip-service` |
| Dừng | `bash scripts/compose.sh down` |
| Build + unit test + ArchUnit | `./mvnw verify` (Windows CMD: `mvnw.cmd verify`). **Docker phải đang chạy**: test DB dùng Testcontainers (Postgres thật) |
| Build 1 service | `./mvnw -pl src/services/trip-service -am verify` |
| Frontend | `cd src/demo-client && npm install && npm run dev` → http://localhost:5173 |
| Eval end-to-end (stack đang chạy) | `bash tests/e2e/regression.sh` (phải **9/9**), `bash tests/e2e/flow-a.sh` (tiến độ = bước FAIL đầu tiên) |
| Gây lỗi khi demo | `bash scripts/fault.sh kill payment-service` … `bash scripts/fault.sh start payment-service` |
| RabbitMQ UI | http://localhost:15672 (user/pass trong `.env`) |

## 2. Git & GitHub

- **Không push thẳng lên `main`.** Mỗi người **1 branch cho 1 nhóm task cùng mảng**, không cần mỗi task 1 branch.
- **Tên branch = phần việc branch đó chứa**, đọc là biết đã làm gì: `feat/<service-hoặc-mảng>-<việc>`, chữ thường, nối bằng `-`, không chỉ ghi mã task. Vd `feat/trip-payment-events` (A3), `feat/login-jwt-passenger-ui` (A4+A5+A7), `feat/matching-saga-compensation` (B1+B2). Thêm việc khác vào branch thì đổi tên cho khớp trước khi mở PR. Sửa lỗi: `fix/<service>-<lỗi>`, vd `fix/trip-history-foreign-key`.
- Trong branch, **mỗi task ít nhất 1 commit riêng**; tiêu đề PR ghi các mã task, vd `A4 A5 A7: đăng nhập + JWT`.
- Merge xong thì xoá branch (bật GitHub *Settings → Automatically delete head branches*); nhóm task tiếp theo tạo branch mới từ `main`.
- Commit bằng **tài khoản GitHub của chính mình** (đề tính đóng góp theo commit). Không commit hộ người khác.
- Commit message: `<loại>(<service>): <việc đã làm>` – loại là `feat`, `fix`, `test`, `docs`, `refactor`, `chore`. Ví dụ: `feat(driver): atomic reserve endpoint`.
- Commit nhỏ, mỗi commit một ý; không gộp nhiều task vào một commit.
- Mở Pull Request vào `main`, cần **1 người khác review**. Xoay vòng reviewer A→B→C→A.
- Cập nhật branch trước khi mở PR: `git pull --rebase origin main`.

### Checklist trước khi mở PR

- [ ] `./mvnw verify` xanh (gồm ArchUnit).
- [ ] `bash scripts/start.sh <service>` rồi `bash tests/e2e/regression.sh` = 9/9.
- [ ] Mô tả PR ghi: task nào, eval nào chuyển FAIL → PASS (vd "flow-a: A1 FAIL → PASS").
- [ ] Đổi API/event → đã sửa file trong `contracts/` **trong cùng PR**.
- [ ] Đổi schema → thêm migration mới `V<n>__...sql`, không sửa file cũ.
- [ ] Có test cho logic mới; không `@Disabled`, không tắt ArchUnit.
- [ ] Không có secret, `.env`, `target/`, `node_modules/` trong diff.

### "Xong" nghĩa là

Code chạy + test xanh + regression 9/9 + contract cập nhật + người khác đã review. Thiếu một trong các điều trên là **chưa xong**.

---

## 3. Quy tắc kiến trúc (R0–R9)

Mã R0–R9 được trích dẫn trong plan và trong review – giữ nguyên số khi bàn luận.

### R0 – Layout repo (bắt buộc theo đề, mục 10.1)

| Thư mục | Chứa |
|---|---|
| `src/` | Mã nguồn: `src/libs/rideflow-common`, `src/services/<svc>`, `src/demo-client` |
| `deploy/` | `docker-compose.yml`, `Dockerfile`, `postgres/` init |
| `tests/` | Kịch bản test end-to-end `tests/e2e/*.sh` (unit/integration test vẫn nằm trong `src/**/src/test`) |
| `scripts/` | Khởi động, gây lỗi, sinh tải: `start.sh`, `compose.sh`, `fault.sh` (+ `load.sh` – task C7) |
| `docs/` | Plan, phân công, `report.pdf`, `slides.pdf` |
| `contracts/` | OpenAPI + AsyncAPI |

Không tạo thư mục gốc mới ngoài các thư mục trên (thư mục cấu hình ẩn như `.mvn/`, `.github/` được phép).

### R1 – Ranh giới service

- 6 service nghiệp vụ: user, driver, trip, matching, payment, notification. Mỗi service sở hữu 1 database (`rideflow_<svc>`).
- **CẤM**: đọc/ghi DB của service khác, import repository/class của service khác, foreign key chéo DB.
- Liên service chỉ qua REST `/internal/**` hoặc event RabbitMQ. Tham chiếu context khác bằng ID (UUID).
- Gateway chỉ route `/api/**`. **KHÔNG** route `/internal/**` ra ngoài.
- `src/libs/rideflow-common` chỉ chứa phần kỹ thuật: `EventEnvelope`, outbox/inbox, correlation, ArchUnit rules, cấu hình chung. **CẤM** đặt domain model dùng chung.

### R2 – Layout bên trong mỗi service (ArchUnit `RideflowArchRules` kiểm tra khi `mvnw verify`)

```text
com.rideflow.<svc>
├── domain            POJO/record thuần. KHÔNG Spring, JPA, AMQP, HTTP, không import application/adapter
│   └── model
├── application       use case (@Service, @Transactional được phép) + port
│   └── port/out      interface repository/client mà use case cần
├── adapter
│   ├── in/web        @RestController mỏng: map HTTP <-> use case
│   ├── in/messaging  @RabbitListener mỏng: inbox guard -> use case
│   └── out/          persistence (JPA entity ≠ domain), messaging, http client
└── infrastructure    @Configuration (queue, bean, security)
```

- Hướng phụ thuộc: adapter → application → domain. Application **không** biết JPA, AMQP, web.
- JPA entity tên `*JpaEntity`, chỉ sống trong `adapter.out.persistence`, có `toDomain()`.
- Lỗi trả về dạng RFC 7807 ProblemDetail: ném `ResponseStatusException` ở adapter web; không tự chế wrapper response.
- Code mẫu để bắt chước: `src/services/notification-service` (listener → use case có inbox → repository).

### R3 – Event

- Exchange topic `rideflow.events`; dead-letter exchange `rideflow.dlx`. Routing key = `eventType`.
- Mọi event là `com.rideflow.common.event.EventEnvelope` (eventId, eventType, eventVersion, correlationId, causationId, aggregateId, producer, occurredAt, payload).
- Tên event lowercase dotted: `trip.created`, `driver.reserved`… Tên là hợp đồng: thêm/đổi phải sửa `contracts/asyncapi/asyncapi.yaml` trong cùng PR.
- **Publish**: gọi `OutboxWriter.append(envelope)` TRONG cùng transaction với thay đổi nghiệp vụ. **CẤM** `rabbitTemplate.send` trực tiếp trong use case (dual write). `OutboxRelay` tự đẩy lên RabbitMQ.
- **Consume**: mỗi consumer có queue riêng, khai báo bằng `RideflowMessaging.consumerQueue("<svc>.<purpose>.q", "routing.key"...)` (tự tạo DLQ `<queue>.dlq`).
- Listener gọi `InboxGuard.firstTime(eventId, consumer, eventType)` trong transaction nghiệp vụ; trả `false` → bỏ qua (duplicate).
- Retry có giới hạn 2s → 5s → 10s rồi vào DLQ (cấu hình chung `rideflow-common.yml`). **CẤM** retry vô hạn, **CẤM** nuốt exception để "đỡ lỗi".
- Không tuyên bố RabbitMQ exactly-once: at-least-once + inbox = exactly-once *business effect*.
- **Sai thứ tự** (đề 4.2): consumer kiểm trạng thái hiện tại trước khi áp dụng event. Event không hợp lệ với trạng thái (vd `payment.succeeded` khi trip chưa COMPLETED, `driver.location-updated` cũ hơn `updated_at` đang lưu) → log WARN và bỏ qua, không ném lỗi (ném lỗi sẽ retry vô ích rồi vào DLQ).

### R4 – Đồng thời & idempotency

- "1 tài xế không bị giữ cho 2 chuyến" chỉ được bảo đảm bằng atomic SQL ở driver-service:
  `UPDATE drivers SET status='RESERVED', current_trip_id=? ... WHERE id=? AND status='AVAILABLE'` → affected rows 1 = thắng, 0 = thua.
- **CẤM** dùng `synchronized`, lock in-memory, hay check-then-update làm cơ chế chính.
- Lệnh có thể retry (reserve, release, assign, payment, consume event) phải idempotent. REST internal nhận header `Idempotency-Key`, lưu ở bảng `idempotency_record`.
- ID: UUID. Thời gian: `Instant` (UTC), cột `TIMESTAMPTZ`.
- Gọi REST `/internal/**` luôn đặt timeout (connect 1s, read 3s) và retry có giới hạn (tối đa 3 lần, cùng `Idempotency-Key`). Hết retry = lỗi vĩnh viễn → saga compensation. **CẤM** chờ vô hạn.

### R5 – Saga ghép tài xế

- matching-service là orchestrator. Lưu `matching_sagas` (status, current_step) **TRƯỚC** khi gọi bước tiếp theo.
- Trạng thái: STARTED → CANDIDATE_SELECTED → DRIVER_RESERVED → TRIP_ASSIGNED → COMPLETED; lỗi: COMPENSATING → COMPENSATED | FAILED.
- Reserve OK nhưng assign thất bại vĩnh viễn → bắt buộc release driver (compensation).
- matching-service **KHÔNG** ghi DB driver/trip; chỉ gọi `/internal/**` của chúng.

### R6 – Quan sát (observability)

- Gateway sinh `X-Correlation-Id` nếu thiếu; service đưa vào MDC (`CorrelationIdFilter`) và vào `EventEnvelope.correlationId`.
- Dùng SLF4J logger. **CẤM** `System.out`/`printStackTrace` (ArchUnit chặn).
- Mỗi service có `/actuator/health`.

### R7 – Frontend (`src/demo-client`)

- Mục đích duy nhất: kích hoạt luồng nghiệp vụ và quan sát hệ thống khi demo. Không làm UI đẹp, không thêm tính năng ngoài demo flow A–E.
- Không chứa business rule, không tự suy ra trạng thái: chỉ hiển thị dữ liệu server trả về và event từ SSE `/api/monitor/events`.
- Chỉ gọi `/api/**` qua gateway (Vite proxy sang :8080). **KHÔNG** gọi thẳng port service.
- Mọi request đi qua `src/api.js` (tự gắn `X-Correlation-Id` để trace được trên Monitor).
- Không thêm UI kit / state library / router. React state + `fetch` + `EventSource`.
- Lỗi API hiển thị nguyên văn ProblemDetail – không giấu lỗi khi demo.

### R8 – Bảo mật

- **KHÔNG** hard-code password, JWT secret, credential. Đọc từ env; `.env` bị gitignore, mẫu ở `.env.example`.
- JWT kiểm ở gateway; service vẫn kiểm role phía server. Không tin kiểm tra role ở frontend.
- Password hash bằng BCrypt. Validate mọi input bên ngoài (`@Valid`). Gateway giới hạn body 64KB.
- Endpoint chaos/replay chỉ tồn tại khi profile `demo` (`@Profile("demo")`).

### R9 – Database & test

- Schema chỉ đổi qua Flyway: thêm `V<n>__<mô_tả>.sql` mới, **KHÔNG** sửa file đã merge. `ddl-auto: validate`.
- Migration thuộc service sở hữu DB.
- Mọi thay đổi nghiệp vụ không tầm thường có test (JUnit 5 + Mockito; Testcontainers cho DB/RabbitMQ).
- Kịch bản phân tán bắt buộc có test: đồng thời (Flow B), event trùng (D), retry/DLQ (C), compensation (E).
- **KHÔNG** tắt ArchUnit hay `@Disabled` test để build xanh.

---

## 4. Dùng AI và mã tham khảo

- Được dùng bất kỳ công cụ AI nào, nhưng **người commit phải đọc, chạy thử và giải thích được từng dòng** trong PR của mình – giảng viên sẽ hỏi khi vấn đáp (đề mục 13).
- Code AI sinh ra vẫn phải qua đủ checklist mục 2 như code tự viết.
- Lấy code/ý tưởng từ nguồn ngoài (thư viện, bài viết, repo khác) → ghi nguồn trong mô tả PR để đưa vào mục References của report.
