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

    public Driver changeAvailability(boolean available) {
        if (available) {
            if (status == DriverStatus.AVAILABLE) {
                return this;
            }
            if (status == DriverStatus.OFFLINE) {
                return new Driver(id, userId, fullName, vehiclePlate, DriverStatus.AVAILABLE,
                        currentTripId, reservedUntil, lat, lng, Instant.now());
            }
            throw new IllegalStateException("Cannot change availability to AVAILABLE while in status " + status);
        } else {
            if (status == DriverStatus.OFFLINE) {
                return this;
            }
            if (status == DriverStatus.AVAILABLE) {
                return new Driver(id, userId, fullName, vehiclePlate, DriverStatus.OFFLINE,
                        currentTripId, reservedUntil, lat, lng, Instant.now());
            }
            throw new IllegalStateException("Cannot change availability to OFFLINE while in status " + status);
        }
    }

    public Driver updateLocation(Double newLat, Double newLng) {
        return new Driver(id, userId, fullName, vehiclePlate, status,
                currentTripId, reservedUntil, newLat, newLng, Instant.now());
    }
}
