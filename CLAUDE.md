# RideFlow – Ride-hailing microservices (DS03)

Mô phỏng Grab/Uber để demo các kỹ thuật hệ phân tán. Plan: `docs/plan/implementation-plan.md`. Phân công: `docs/plan/team-split.md`. Quy trình agent (eval-first, chia unit, chọn model): `docs/plan/agentic-workflow.md`.

## Thứ tự ưu tiên khi tài liệu mâu thuẫn
1. Rules trong `.claude/rules/` (file này + các file rule)
2. Contracts: `contracts/asyncapi/asyncapi.yaml`, `contracts/openapi/public-api.yaml`
3. `docs/plan/implementation-plan.md`

## Lệnh hay dùng
- Build + test (ArchUnit): `./mvnw verify` (Windows: `mvnw.cmd verify`)
- Chạy cả hệ thống: `cp .env.example .env && bash scripts/start.sh`
- Frontend: `cd src/demo-client && npm install && npm run dev` → http://localhost:5173
- RabbitMQ UI: http://localhost:15672
- Eval (stack đang chạy): `bash tests/e2e/regression.sh` (phải 9/9 ở mọi PR), `bash tests/e2e/flow-a.sh` (tiến độ Flow A = bước FAIL đầu tiên)

## Trước khi code một thay đổi không trivial
1. Xác định service bị ảnh hưởng và file cần sửa.
2. Kiểm tra có đổi API / event / schema không → nếu có, sửa contract + Flyway migration mới (không sửa migration cũ).
3. Code theo layout ở `.claude/rules/architecture.md`, mirror code sẵn có trong service.
4. Viết test, chạy `./mvnw -pl src/services/<svc> -am verify`. Test đỏ = chưa xong.
5. Rebuild service (`bash scripts/start.sh <svc>`), chạy eval; ghi failure signature trước/sau vào PR.

## Rules
- `.claude/rules/architecture.md` – R1 ranh giới service, R2 layout, R5 saga
- `.claude/rules/messaging.md` – R3 event/outbox/inbox/retry, R4 concurrency & idempotency
- `.claude/rules/frontend.md` – R7 demo-client
- `.claude/rules/security-testing.md` – R6 observability, R8 bảo mật, R9 DB & test
