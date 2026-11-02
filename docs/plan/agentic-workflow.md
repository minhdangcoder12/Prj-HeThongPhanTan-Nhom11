# Quy trình agentic – RideFlow

Áp dụng skill `ecc:agentic-engineering` cho dự án: **agent (Claude Code) viết phần lớn code, người kiểm soát chất lượng và rủi ro.** Đọc cùng `team-split.md` (ai làm gì) và `CONTRIBUTING.md` (luật bắt buộc).

## 1. Eval trước, code sau

Mỗi demo flow có **1 script eval chạy được** trên stack thật. Script được viết **trước** khi code, chạy ra baseline đỏ, rồi code cho tới khi xanh.

| Eval | Loại | Chủ | Trạng thái baseline (2026-11-02) |
|---|---|---|---|
| `tests/e2e/regression.sh` | Regression – **phải xanh ở mọi PR** | cả nhóm | **9/9 PASS** (health, routes, ProblemDetail, chặn /internal, correlation id, SSE, Flow D inbox) |
| `tests/e2e/flow-a.sh` | Capability – Flow A happy path | A (B, C góp bước) | **FAIL ở A1**: `404 No static resource api/drivers/.../availability` (B4 chưa làm) |
| `tests/e2e/flow-b.sh` | Capability – concurrency | C viết, B làm xanh | chưa có – viết ở task C5 |
| `tests/e2e/flow-c.sh` | Capability – retry/DLQ | C | chưa có |
| `tests/e2e/flow-e.sh` | Capability – compensation | B viết, A + B làm xanh | chưa có |
| Flow D | đã nằm trong regression R7–R8 | C | PASS |

Vòng lặp cho mỗi task:

1. Chạy eval liên quan → ghi **failure signature** (bước đầu tiên FAIL + HTTP code + body) vào mô tả PR.
2. Agent code (xem §3 chia nhỏ).
3. Chạy lại: `./mvnw -pl src/services/<svc> -am verify` → `bash scripts/start.sh <svc>` → eval.
4. PR ghi **delta**: bước nào chuyển FAIL → PASS; regression vẫn 9/9.

Tiến độ Flow A đo bằng **bước đầu tiên còn FAIL**: A1 → A2 → … → A8. Đây là thước đo tiến độ duy nhất nhóm cần nhìn.

## 2. Định nghĩa "xong" cho một đơn vị việc

Một đơn vị chỉ xong khi **cả 3** đúng:
- Có lệnh kiểm chứng chạy được (unit test, `verify`, hoặc 1 bước eval) và nó xanh.
- `regression.sh` vẫn 9/9.
- Contract đã cập nhật nếu đổi API/event (rule R3).

## 3. Chia task thành đơn vị ~15 phút

Quy tắc: mỗi đơn vị **kiểm chứng độc lập**, có **1 rủi ro chính**, có **điều kiện xong rõ ràng**. Dưới đây là cách chia 3 task 5 điểm (khó nhất); task nhỏ hơn tự chia theo mẫu này.

### A1 – Vòng đời chuyến (người A)

| Unit | Việc | Rủi ro chính | Xong khi | Model |
|---|---|---|---|---|
| A1.1 | Bảng chuyển trạng thái trong `TripStatus` + `Trip.transitionTo()` | Bỏ sót cặp chuyển sai | Unit test tham số hóa mọi cặp (hợp lệ/không) | Sonnet |
| A1.2 | `CreateTrip` use case + validate + `POST /api/trips` | Input không validate | `flow-a.sh` qua A3 | Sonnet |
| A1.3 | Ghi `trip_status_history` mỗi lần chuyển | Quên ghi ở 1 nhánh | Test đếm history = số lần chuyển | Haiku |
| A1.4 | Outbox `trip.*` trong cùng transaction | Dual write | Test: rollback use case ⇒ không có dòng outbox | Sonnet |
| A1.5 | accept/start/complete endpoint + 409 khi sai trạng thái | Sai mã lỗi | `flow-a.sh` qua A5 (sau khi B xong A4) | Sonnet |

### B1 + B2 – Saga (người B)

| Unit | Việc | Rủi ro chính | Xong khi | Model |
|---|---|---|---|---|
| B1.1 | Thiết kế: state machine saga, port `DriverLocator`, `DriverClient`, `TripClient` | Thiết kế sai làm hỏng mọi bước sau | Review người + bảng trạng thái trong PR | **Opus** |
| B1.2 | Consumer `trip.created` + inbox + tạo saga STARTED | Tạo 2 saga cho 1 trip | Test duplicate event ⇒ 1 saga (unique `trip_id`) | Sonnet |
| B1.3 | Bước reserve: lưu trạng thái **trước** khi gọi, thử ứng viên kế khi 409 | Mất trạng thái khi crash giữa bước | Test: client ném lỗi giữa chừng ⇒ saga vẫn ở bước đúng | Sonnet |
| B1.4 | Bước assign + COMPLETED + `matching.saga-changed` | Gọi assign không có Idempotency-Key | `flow-a.sh` qua A4 | Sonnet |
| B2.1 | Compensation COMPENSATING → release → COMPENSATED | Release gọi 2 lần / không gọi | Test Mockito: assign lỗi ⇒ release đúng 1 lần | **Opus** review, Sonnet code |
| B2.2 | Hết ứng viên ⇒ FAILED + báo trip `no-driver` | Trip kẹt MATCHING | `flow-e.sh` xanh | Sonnet |

### C1 + C2 + C3 – Reliability (người C)

| Unit | Việc | Rủi ro chính | Xong khi | Model |
|---|---|---|---|---|
| C1.1 | Consumer `trip.completed` + inbox + payment PENDING (unique `trip_id`) | Thu tiền 2 lần | Test duplicate ⇒ 1 payment | Sonnet |
| C1.2 | Giả lập thanh toán + `payment.*` qua outbox | Kết quả không ổn định khi test | Tỉ lệ lỗi inject qua config; test với 0 và 1 | Sonnet |
| C2.1 | Chaos fail-rate (`@Profile("demo")`) | Lỡ bật ở profile thường | Test context không profile demo ⇒ không có bean | Haiku |
| C2.2 | Quan sát retry 2s/5s/10s → DLQ | Retry vô hạn | `flow-c.sh`: message vào `.dlq` sau ~17s, đúng 4 lần thử trong log | Sonnet |
| C3.1 | Reprocess DLQ → queue chính | Mất message khi đẩy lại | `flow-c.sh`: reprocess ⇒ notification SENT, DLQ rỗng | Sonnet |

## 4. Chọn model trong Claude Code

| Model | Dùng cho | Ví dụ trong repo |
|---|---|---|
| **Haiku** | Boilerplate, chỉnh hẹp, phân loại | Migration `V<n>__*.sql`, DTO, thêm cột vào `*JpaEntity`, sửa contract yaml theo code |
| **Sonnet** (mặc định) | Viết use case, endpoint, consumer, test | Đa số unit trong §3 |
| **Opus** | Kiến trúc, bất biến nhiều file, tìm nguyên nhân gốc | Thiết kế saga (B1.1), review compensation (B2.1), atomic reserve (B3), debug lỗi chỉ xảy ra khi chạy đồng thời |

Chỉ **nâng model** khi model thấp hơn thất bại vì **thiếu suy luận** (sai logic, bỏ sót bất biến), không phải vì thiếu context – thiếu context thì bổ sung context.

## 5. Chiến lược session

- **Cùng 1 session** cho các unit dính chặt nhau (A1.1 → A1.5).
- **Session mới** khi chuyển phase hoặc chuyển service (tránh agent mang giả định của service này sang service khác).
- `/compact` **sau** khi xong 1 milestone (eval chuyển bước), **không** compact giữa lúc đang debug.
- Mở đầu mỗi session: "Đọc `CONTRIBUTING.md`, task `<mã>` trong `docs/plan/team-split.md`, chạy `bash tests/e2e/flow-a.sh` và cho biết failure signature."

## 6. Review code do agent viết – soi gì

Không tranh luận style (formatter/ArchUnit đã lo). Tập trung vào các bất biến của dự án:

| Kiểm | Câu hỏi khi review |
|---|---|
| Atomic reservation (R4) | Có đúng 1 câu `UPDATE ... WHERE status='AVAILABLE'` và kiểm affected rows? Có `synchronized`/check-then-update lén vào không? |
| Outbox (R3) | `OutboxWriter.append` nằm **trong** `@Transactional` cùng thay đổi nghiệp vụ? Có `rabbitTemplate.send` trực tiếp trong use case không? |
| Inbox (R3) | `InboxGuard.firstTime` gọi trong cùng transaction với side effect? Nuốt exception để "đỡ lỗi" → phá retry/DLQ? |
| Idempotency (R4) | Lệnh retry được có `Idempotency-Key`? Gọi lại trả cùng kết quả, không làm 2 lần? |
| Ranh giới (R1) | Có import class/repository của service khác? Gateway có lỡ route `/internal/**`? |
| Bảo mật (R8) | Secret hard-code? Chaos endpoint thiếu `@Profile("demo")`? Role chỉ kiểm ở frontend? |
| Lỗi | Lỗi trả ProblemDetail với mã đúng (400/404/409), không 500 chung chung? |

Khi đọc kết quả tool/HTTP trong eval: **kiểm mã HTTP và trạng thái thao tác trước**, rồi mới so field. 404 do chưa có endpoint là "operation error", không phải "dữ liệu sai".

## 7. Ghi chi phí

Mỗi PR thêm 1 dòng vào mô tả PR:

```text
model: sonnet | tokens ~: 120k | retries: 1 | thời gian: 40m | eval: flow-a A3 FAIL→PASS, regression 9/9
```

Cuối mỗi tuần nhóm cộng lại để biết task nào tốn nhiều lượt agent nhất → đó thường là chỗ thiết kế chưa rõ, cần người nhìn kỹ.
