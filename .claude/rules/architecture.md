# Architecture rules

## R0 – Layout repo (bắt buộc theo đề, mục 10.1)
| Thư mục | Chứa |
|---|---|
| `src/` | Mã nguồn: `src/libs/rideflow-common`, `src/services/<svc>`, `src/demo-client` |
| `deploy/` | `docker-compose.yml`, `Dockerfile`, `postgres/` init |
| `tests/` | Kịch bản test end-to-end `tests/e2e/*.sh` (unit/integration test vẫn nằm trong `src/**/src/test`) |
| `scripts/` | Khởi động, gây lỗi, sinh tải: `start.sh`, `compose.sh`, `fault.sh` (+ `load.sh` – task C7) |
| `docs/` | Plan, phân công, `report.pdf`, `slides.pdf` |

Không tạo thư mục gốc mới ngoài các thư mục trên.

## R1 – Ranh giới service
- 6 service nghiệp vụ: user, driver, trip, matching, payment, notification. Mỗi service sở hữu 1 database (`rideflow_<svc>`).
- CẤM: đọc/ghi DB của service khác, import repository/class của service khác, foreign key chéo DB.
- Liên service chỉ qua REST `/internal/**` hoặc event RabbitMQ. Tham chiếu context khác bằng ID (UUID).
- Gateway chỉ route `/api/**`. KHÔNG route `/internal/**` ra ngoài.
- `src/libs/rideflow-common` chỉ chứa technical: `EventEnvelope`, outbox/inbox, correlation, ArchUnit rules, cấu hình chung. CẤM đặt domain model dùng chung.

## R2 – Layout bên trong mỗi service (ArchUnit `RideflowArchRules` kiểm tra khi `mvnw verify`)
```
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
- Hướng phụ thuộc: adapter → application → domain. Application KHÔNG biết JPA, AMQP, web.
- JPA entity tên `*JpaEntity`, chỉ sống trong `adapter.out.persistence`, có `toDomain()`.
- Lỗi trả về dạng RFC 7807 ProblemDetail: ném `ResponseStatusException` ở adapter web; không tự chế wrapper response.

## R5 – Saga ghép tài xế
- matching-service là orchestrator. Lưu `matching_sagas` (status, current_step) TRƯỚC khi gọi bước tiếp theo.
- Trạng thái: STARTED → CANDIDATE_SELECTED → DRIVER_RESERVED → TRIP_ASSIGNED → COMPLETED; lỗi: COMPENSATING → COMPENSATED | FAILED.
- Reserve OK nhưng assign thất bại vĩnh viễn → bắt buộc release driver (compensation).
- matching-service KHÔNG ghi DB driver/trip; chỉ gọi `/internal/**` của chúng.
