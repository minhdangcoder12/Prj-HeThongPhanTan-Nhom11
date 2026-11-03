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

    public Driver reserve(UUID tripId, Instant reservedUntil) {
        if (status != DriverStatus.AVAILABLE) {
            throw new IllegalStateException("Cannot reserve driver in status " + status);
        }
        if (tripId == null) {
            throw new IllegalArgumentException("tripId cannot be null");
        }
        return new Driver(id, userId, fullName, vehiclePlate, DriverStatus.RESERVED,
                tripId, reservedUntil, lat, lng, Instant.now());
    }

    public Driver release() {
        if (status == DriverStatus.AVAILABLE) {
            return this;
        }
        if (status != DriverStatus.RESERVED) {
            throw new IllegalStateException("Cannot release driver in status " + status);
        }
        return new Driver(id, userId, fullName, vehiclePlate, DriverStatus.AVAILABLE,
                null, null, lat, lng, Instant.now());
    }
}
