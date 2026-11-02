package com.rideflow.driver.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). Put behaviour here, not in adapters. */
public record Driver(
        UUID id,
        UUID userId,
        String fullName,
        String vehiclePlate,
        DriverStatus status,
        UUID currentTripId,
        Instant reservedUntil,
        Double lat,
        Double lng,
        Instant updatedAt) {
}
