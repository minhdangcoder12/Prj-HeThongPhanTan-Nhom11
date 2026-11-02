# Observability, security, DB & testing rules

## R6 – Observability
- Gateway sinh `X-Correlation-Id` nếu thiếu; service đưa vào MDC (`CorrelationIdFilter`) và vào `EventEnvelope.correlationId`.
- Dùng SLF4J logger. CẤM `System.out`/`printStackTrace` (ArchUnit chặn).
- Mỗi service có `/actuator/health`.

## R8 – Bảo mật
- KHÔNG hard-code password, JWT secret, credential. Đọc từ env; `.env` bị gitignore, mẫu ở `.env.example`.
- JWT kiểm ở gateway; service vẫn kiểm role phía server. Không tin kiểm tra role ở frontend.
- Password hash bằng BCrypt. Validate mọi input bên ngoài (`@Valid`).
- Endpoint chaos/replay chỉ tồn tại khi profile `demo` (`@Profile("demo")` / `application-demo.yml`).

## R9 – DB & test
- Schema chỉ đổi qua Flyway: thêm `V<n>__<mô_tả>.sql` mới, KHÔNG sửa file đã merge. `ddl-auto: validate`.
- Migration thuộc service sở hữu DB.
- Mọi thay đổi nghiệp vụ không trivial có test (JUnit 5 + Mockito; Testcontainers cho DB/RabbitMQ).
- Kịch bản phân tán bắt buộc có test: concurrency (Flow B), duplicate event (D), retry/DLQ (C), compensation (E).
- KHÔNG tắt ArchUnit hay `@Disabled` test để build xanh.
