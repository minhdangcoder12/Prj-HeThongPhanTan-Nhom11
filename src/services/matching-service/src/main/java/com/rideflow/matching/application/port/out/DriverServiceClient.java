package com.rideflow.matching.application.port.out;

import java.util.UUID;

public interface DriverServiceClient {
    boolean reserve(UUID driverId, UUID tripId, String idempotencyKey);
    void release(UUID driverId, UUID tripId, String idempotencyKey);
}

