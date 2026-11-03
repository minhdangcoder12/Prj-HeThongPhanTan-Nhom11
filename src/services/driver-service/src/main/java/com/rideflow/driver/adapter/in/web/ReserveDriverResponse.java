package com.rideflow.driver.adapter.in.web;

import com.rideflow.driver.domain.model.DriverStatus;

import java.time.Instant;
import java.util.UUID;

public record ReserveDriverResponse(
        UUID driverId,
        DriverStatus status,
        UUID tripId,
        Instant reservedUntil
) {}

