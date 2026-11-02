# RideFlow – hướng dẫn cho Claude Code

Quy tắc chung của cả nhóm nằm ở `CONTRIBUTING.md` – đọc và tuân thủ trước khi code (dòng dưới tự nạp nó):

@CONTRIBUTING.md

Riêng khi dùng Claude Code:
- Phân công và lịch: `docs/plan/team-split.md`. Quy trình agent (eval-first, chia unit, chọn model): `docs/plan/agentic-workflow.md`.
- Task ≥ 3 điểm: `/ecc:plan` trước; khi code dùng skill `ecc:tdd-workflow`; trước PR chạy `/ecc:code-review`.
- Mở đầu session: chạy eval liên quan (`bash tests/e2e/flow-a.sh`…) và nêu failure signature trước khi sửa.
