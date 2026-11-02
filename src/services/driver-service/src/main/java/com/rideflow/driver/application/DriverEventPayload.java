package com.rideflow.driver.application;

import com.rideflow.driver.domain.model.DriverStatus;

import java.util.UUID;

public record DriverEventPayload(
        UUID driverId,
        DriverStatus status,
        UUID tripId,
        Location location
) {
    public record Location(Double lat, Double lng) {}
}
