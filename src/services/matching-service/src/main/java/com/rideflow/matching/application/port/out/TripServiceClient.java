package com.rideflow.matching.application.port.out;

import java.util.UUID;

public interface TripServiceClient {
    boolean assignDriver(UUID tripId, UUID driverId, String idempotencyKey);
}

