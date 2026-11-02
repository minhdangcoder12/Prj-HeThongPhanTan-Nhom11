# RideFlow – Ride-hailing microservices (DS03)

Đồ án môn Hệ thống phân tán, topic **DS03**: mô phỏng Grab/Uber bằng microservices, minh hoạ **REST + message queue, Saga/compensation, atomic reservation, Transactional Outbox/Inbox, idempotency, bounded retry + DLQ, correlation ID**.

- Kế hoạch, đối chiếu đề bài: [`docs/plan/implementation-plan.md`](docs/plan/implementation-plan.md) (§11)
- Phân công, ma trận test, lịch đến 22/11: [`docs/plan/team-split.md`](docs/plan/team-split.md)
- Quy trình làm việc với AI agent: [`docs/plan/agentic-workflow.md`](docs/plan/agentic-workflow.md)
- **Quy tắc làm việc chung (mọi thành viên phải đọc): [`CONTRIBUTING.md`](CONTRIBUTING.md)**
- Hợp đồng: [`contracts/asyncapi/asyncapi.yaml`](contracts/asyncapi/asyncapi.yaml), [`contracts/openapi/public-api.yaml`](contracts/openapi/public-api.yaml)

## Kiến trúc

```text
React+Vite demo-client :5173 ──REST + SSE──► api-gateway :8080 (routes /api/**, correlation id, size limit, event tap)
                                                   │
   user :8081   driver :8082   trip :8083   matching :8084 (+Redis GEO)   payment :8085   notification :8086
      │            │             │              │                            │                │
      └──── PostgreSQL (1 container, 6 database) ── RabbitMQ topic `rideflow.events` + DLX `rideflow.dlx` ────┘
```

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
| A – _(họ tên)_ | | user-service, trip-service (vòng đời, tính cước), JWT gateway, Passenger panel | A, E (trip) | 4, 9, 12, 13 |
| B – _(họ tên)_ | | driver-service (atomic reservation), matching-service (Saga), Driver panel | B, E (saga) | 7, 8, 10 |
| C – _(họ tên)_ | | payment, notification (retry/DLQ), Redis GEO, CI, đo hiệu năng, Monitor | C, D, restart | 11, 14, 15 |

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
