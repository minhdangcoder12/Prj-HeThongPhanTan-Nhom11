package com.rideflow.trip.application.port.out;

import java.util.Optional;

/** Stored result of a command sent with an {@code Idempotency-Key} (table idempotency_record, rule R4). */
public interface IdempotencyRepository {

    record CachedResponse(int statusCode, String body) {}

    Optional<CachedResponse> find(String key, String operation);

    /** {@code body} is JSON. */
    void save(String key, String operation, int statusCode, String body);
}
