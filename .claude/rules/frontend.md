# Frontend rules (src/demo-client)

## R7
- Mục đích duy nhất: kích hoạt luồng nghiệp vụ và quan sát hệ thống khi demo. Không làm UI đẹp, không thêm tính năng ngoài demo flow A–E.
- Không chứa business rule, không tự suy ra trạng thái: chỉ hiển thị dữ liệu server trả về và event từ SSE `/api/monitor/events`.
- Chỉ gọi `/api/**` qua gateway (Vite proxy sang :8080). KHÔNG gọi thẳng port service.
- Mọi request đi qua `src/api.js` (tự gắn `X-Correlation-Id` để trace được trên Monitor).
- Không thêm UI kit / state library / router. React state + `fetch` + `EventSource`.
- Lỗi API hiển thị nguyên văn ProblemDetail – không giấu lỗi khi demo.
