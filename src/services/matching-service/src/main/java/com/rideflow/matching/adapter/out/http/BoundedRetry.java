package com.rideflow.matching.adapter.out.http;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * R4: gọi lại tối đa 3 lần với cùng Idempotency-Key khi lỗi mạng, timeout hay 5xx (bên kia có thể đã làm xong,
 * key giữ cho lần gọi lại không làm lần hai). Lỗi 4xx là câu trả lời thật nên trả về ngay, không thử lại.
 */
final class BoundedRetry {

    static final int MAX_ATTEMPTS = 3;

    private BoundedRetry() {
    }

    static <T> T call(Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException | HttpServerErrorException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                pause(200L * attempt);
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying", e);
        }
    }
}
