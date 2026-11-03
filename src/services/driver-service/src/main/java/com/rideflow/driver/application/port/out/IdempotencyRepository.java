package com.rideflow.driver.application.port.out;

import java.util.Optional;

public interface IdempotencyRepository {

    record CachedResponse(int statusCode, String body) {}

    Optional<CachedResponse> find(String key, String operation);

    void save(String key, String operation, int statusCode, String body);
}

