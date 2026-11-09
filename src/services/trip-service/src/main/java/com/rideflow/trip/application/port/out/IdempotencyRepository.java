package com.rideflow.trip.application.port.out;

import java.util.Optional;

/** Kết quả đã lưu của lệnh có Idempotency-Key (bảng idempotency_record, R4). */
public interface IdempotencyRepository {

    record CachedResponse(int statusCode, String body) {}

    Optional<CachedResponse> find(String key, String operation);

    /** {@code body} là JSON. */
    void save(String key, String operation, int statusCode, String body);
}
