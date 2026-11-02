# RideFlow – Ride-hailing microservices (DS03)

Đồ án môn Hệ thống phân tán, topic **DS03**: mô phỏng Grab/Uber bằng microservices, minh hoạ **REST + message queue, Saga/compensation, atomic reservation, Transactional Outbox/Inbox, idempotency, bounded retry + DLQ, correlation ID**.

- Kế hoạch, đối chiếu đề bài: [`docs/plan/implementation-plan.md`](docs/plan/implementation-plan.md) (§11)
- Phân công, ma trận test, lịch đến 22/11: [`docs/plan/team-split.md`](docs/plan/team-split.md)
- Quy trình làm việc với AI agent: [`docs/plan/agentic-workflow.md`](docs/plan/agentic-workflow.md)
- **Quy tắc làm việc chung (mọi thành viên phải đọc): [`CONTRIBUTING.md`](CONTRIBUTING.md)**
- Hợp đồng: [`contracts/asyncapi/asyncapi.yaml`](contracts/asyncapi/asyncapi.yaml), [`contracts/openapi/public-api.yaml`](contracts/openapi/public-api.yaml)

## Kiến trúc

### Tổng quan

```mermaid
flowchart TB
    UI["🖥️ Demo client · React + Vite · :5173"]
    GW["🚪 API Gateway · :8080<br/>định tuyến /api/** · correlation ID · giới hạn body 64KB"]

    subgraph BIZ["6 service nghiệp vụ – mỗi service 1 database PostgreSQL riêng"]
        direction LR
        USER["👤 user<br/>:8081"]
        TRIP["🧾 trip<br/>:8083"]
        MATCH["🧭 matching<br/>:8084"]
        DRIVER["🚗 driver<br/>:8082"]
        PAY["💳 payment<br/>:8085"]
        NOTI["🔔 notification<br/>:8086"]
        MATCH -->|"assign-driver"| TRIP
        MATCH -->|"reserve · release"| DRIVER
    end

    MQ{{"🐇 RabbitMQ · rideflow.events + DLX rideflow.dlx"}}
    REDIS[("Redis GEO")]

    UI -->|"REST + SSE"| GW
    GW -->|"REST /api/**"| BIZ
    MATCH --- REDIS
    BIZ <-.->|"event"| MQ
    MQ -.->|"mọi event → màn hình Monitor"| GW
```

**Đường liền** = gọi REST đồng bộ · **Đường đứt** = event bất đồng bộ qua RabbitMQ.

### Các service

| Service | Port | Database | Nhiệm vụ chính | Phụ trách |
|---|---|---|---|---|
| api-gateway | 8080 | – | Cửa vào duy nhất `/api/**`, gắn `X-Correlation-Id`, chặn body > 64KB, đẩy event cho Monitor qua SSE | Cả nhóm |
| user-service | 8081 | `rideflow_user` | Đăng ký (role khách/tài xế), đăng nhập, JWT | A |
| driver-service | 8082 | `rideflow_driver` | Trạng thái, vị trí tài xế; **giữ chỗ nguyên tử** (1 tài xế không nhận 2 chuyến) | B |
| trip-service | 8083 | `rideflow_trip` | Vòng đời chuyến đi, tính cước | A |
| matching-service | 8084 | `rideflow_matching` + Redis | Tìm tài xế gần nhất, điều phối **Saga** và compensation | B (Saga), C (Redis) |
| payment-service | 8085 | `rideflow_payment` | Giả lập thanh toán khi chuyến hoàn thành | C |
| notification-service | 8086 | `rideflow_notification` | Gửi thông báo cho khách và tài xế; retry → DLQ | C |

### Giao tiếp

| Kiểu | Dùng khi | Cơ chế tin cậy |
|---|---|---|
| **REST đồng bộ** | Client → gateway → service; matching gọi `/internal/**` của driver/trip | Timeout, retry có giới hạn, `Idempotency-Key` |
| **Event bất đồng bộ** | Thông báo thay đổi trạng thái (`trip.created`, `driver.reserved`, `payment.succeeded`…) | Transactional Outbox khi gửi, Inbox chống trùng khi nhận, retry 2s → 5s → 10s rồi vào DLQ |
| **SSE** | Gateway đẩy toàn bộ event lên màn hình Monitor | Chỉ để quan sát khi demo |

### Luồng chính: đặt xe → hoàn thành → thanh toán

```mermaid
sequenceDiagram
    autonumber
    actor P as Khách
    participant T as trip-service
    participant M as matching-service
    participant D as driver-service
    participant Pay as payment-service
    participant N as notification-service

    P->>T: Đặt xe (POST /api/trips) – tính cước
    T-)M: trip.created
    M->>D: Giữ chỗ tài xế gần nhất (REST)
    alt Tài xế đã bị khách khác giữ
        D-->>M: 409 → thử tài xế kế tiếp
    end
    M->>T: Gán tài xế (REST)
    alt Gán thất bại
        M->>D: Thả tài xế (compensation)
    end
    T-)N: trip.driver-assigned → báo khách và tài xế
    Note over T: Tài xế nhận → bắt đầu → hoàn thành
    T-)Pay: trip.completed
    Pay-)T: payment.succeeded → chuyến PAID
    Pay-)N: báo đã thanh toán
```

Mũi tên `->>` là REST, mũi tên `-)` là event qua RabbitMQ.

## Cấu trúc repo

| Thư mục | Nội dung |
|---|---|
| `src/` | `libs/rideflow-common` (technical dùng chung), `services/<7 service>`, `demo-client` (React + Vite) |
| `deploy/` | `docker-compose.yml`, `Dockerfile` dùng chung, init Postgres |
| `tests/e2e/` | Kịch bản test end-to-end chạy trên stack thật (unit/integration test nằm trong từng service) |
| `scripts/` | `start.sh`, `compose.sh`, `fault.sh` (gây lỗi), `load.sh` (sinh tải – task C7) |
| `docs/` | Plan, phân công, `report.pdf`, `slides.pdf` |
| `contracts/` | OpenAPI + AsyncAPI |

## Cài đặt & chạy (cũng là hướng dẫn redeploy)

Yêu cầu: Docker Desktop, Git Bash (Windows) hoặc bash, Node 18+. JDK 21+ chỉ cần khi chạy service ngoài Docker.

```bash
cp .env.example .env              # đổi mật khẩu trong .env, không commit file này
bash scripts/start.sh             # build + chạy 10 container, chờ tới khi mọi service UP
cd src/demo-client && npm install && npm run dev   # http://localhost:5173
```

- RabbitMQ UI: http://localhost:15672 (user/pass trong `.env`)
- Chạy riêng 1 service: `bash scripts/compose.sh up -d --build trip-service`
- Chỉ hạ tầng (chạy service từ IDE): `bash scripts/compose.sh up -d postgres rabbitmq redis`
- Dừng tất cả: `bash scripts/compose.sh down` (thêm `-v` để xoá dữ liệu)

## Build & test

```bash
./mvnw verify                                      # Windows: mvnw.cmd verify — unit test + ArchUnit
bash tests/e2e/regression.sh                       # stack đang chạy; phải 9/9
bash tests/e2e/flow-a.sh                           # Flow A; bước FAIL đầu tiên = tiến độ
bash tests/e2e/flow-restart.sh                     # tắt payment giữa luồng → bật lại → PAID
```

## Kịch bản demo (đề mục 7.4)

| # | Kịch bản | Cách chạy | Người trình bày |
|---|---|---|---|
| 1 | Start service độc lập; tài xế online | `scripts/compose.sh up -d <svc>`; Driver panel → Online | B |
| 2 | Khách đặt xe → Matching chọn tài xế → nhận → hoàn thành → thanh toán → thông báo (Flow A) | Passenger + Driver panel; theo dõi Events trên Monitor | A |
| 3 | 2 khách cùng lúc tranh 1 tài xế (Flow B) | Nút "Flow B: 2 khách cùng lúc" | B |
| 4 | Tắt service giữa luồng → message chờ trong queue → bật lại xử lý tiếp | `bash scripts/fault.sh kill payment-service` … `start` | C |
| 5 | Retry → DLQ → reprocess (Flow C) | Chaos fail-rate trên panel Kịch bản demo | C |
| 6 | Event trùng → xử lý 1 lần (Flow D) | Nút "replay" trên một event | C |
| 7 | Gán tài xế lỗi → Saga compensation (Flow E) | "Bật lỗi assign" rồi đặt xe | A + B |

## Phân công

Chi tiết từng task, điểm độ khó và ma trận test: [`docs/plan/team-split.md`](docs/plan/team-split.md).

| Thành viên | MSSV | Phụ trách | Demo flow | Chương report |
|---|---|---|---|---|
| A – Nguyễn Minh Đăng | | user-service, trip-service (vòng đời, tính cước), JWT gateway, Passenger panel | A, E (trip) | 1–3, 4, 9, 12 + phần ch.13 |
| B – Phạm Hải Long | | driver-service (atomic reservation), matching-service (Saga), Driver panel | B, E (saga) | 5, 6, 7, 8, 10 + phần ch.13; tổng hợp report |
| C – Lê Nguyên Nhật Minh | | payment, notification (retry/DLQ), Redis GEO, CI, đo hiệu năng, Monitor | C, D, restart | 11, 14, 15, 16–19 + phần ch.13; slides |

## Trạng thái

| Có sẵn (Phase 0) | Đang làm (xem team-split) |
|---|---|
| 7 service khởi động, health, Flyway schema, API đọc cho Monitor | Đặt xe, reserve, Saga, thanh toán, tính cước |
| `rideflow-common`: `EventEnvelope`, Outbox/Inbox, correlation filter, retry 2s/5s/10s → DLQ | JWT / đăng ký có role |
| Consumer mẫu có inbox (notification-service) | Chaos endpoint phía service, DLQ reprocess |
| Gateway: SSE monitor, health tổng, replay event, giới hạn body 64KB | Redis GEO projection |
| Eval: `regression.sh` 9/9; `flow-a.sh`, `flow-restart.sh` (đang đỏ – đúng kỳ vọng) | `flow-b/c/e.sh`, `scripts/load.sh` |

## Nguồn tham khảo & sử dụng AI

- Thư viện: Spring Boot 3.3, Spring Cloud Gateway, Spring AMQP, Flyway, ArchUnit, React, Vite.
- Khung codebase và tài liệu được dựng với hỗ trợ của Claude Code (Anthropic). Nhóm đã đọc, chạy, kiểm chứng và chịu trách nhiệm với toàn bộ mã nguồn (đề mục 13).
