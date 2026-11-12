package com.rideflow.trip.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Domain thuần Java (R2). Mọi lần đổi trạng thái đều qua {@link TripStatus#canMoveTo}. */
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
        BigDecimal surgeMultiplier,
        Instant createdAt,
        Instant updatedAt) {

    public static Trip request(UUID passengerId, double pickupLat, double pickupLng,
                               double dropoffLat, double dropoffLng, BigDecimal surgeMultiplier, Instant now) {
        return new Trip(UUID.randomUUID(), passengerId, null, TripStatus.REQUESTED, pickupLat, pickupLng,
                dropoffLat, dropoffLng,
                Fare.withSurge(Fare.estimate(pickupLat, pickupLng, dropoffLat, dropoffLng), surgeMultiplier),
                surgeMultiplier, now, now);
    }

    public Trip transitionTo(TripStatus next, Instant now) {
        return withStatus(next, driverId, now);
    }

    public Trip assignDriver(UUID driver, Instant now) {
        return withStatus(TripStatus.DRIVER_ASSIGNED, driver, now);
    }

    /** Tài xế từ chối: chuyến quay lại MATCHING và bỏ tài xế đó. */
    public Trip rejectByDriver(Instant now) {
        return withStatus(TripStatus.MATCHING, null, now);
    }

    private Trip withStatus(TripStatus next, UUID driver, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidTripTransitionException(status, next);
        }
        return new Trip(id, passengerId, driver, next, pickupLat, pickupLng, dropoffLat, dropoffLng, fare, surgeMultiplier, createdAt, now);
    }
}
