package com.rideflow.trip.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). Put behaviour here, not in adapters. */
public record Trip(
        UUID id,
        UUID passengerId,
        UUID driverId,
        TripStatus status,
        Double pickupLat,
        Double pickupLng,
        Double dropoffLat,
        Double dropoffLng,
        BigDecimal fare,
        Instant createdAt,
        Instant updatedAt) {
}
