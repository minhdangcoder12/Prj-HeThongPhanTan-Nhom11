package com.rideflow.trip.application;

import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Payload của mọi event trip.*, theo TripEnvelope trong contracts/asyncapi/asyncapi.yaml. */
public record TripEventPayload(UUID tripId, UUID passengerId, UUID driverId, TripStatus status,
                               Point pickup, Point dropoff, BigDecimal fare, BigDecimal surgeMultiplier) {

    public record Point(double lat, double lng) {}

    static TripEventPayload of(Trip trip, UUID driverId) {
        return new TripEventPayload(trip.id(), trip.passengerId(), driverId, trip.status(),
                new Point(trip.pickupLat(), trip.pickupLng()), new Point(trip.dropoffLat(), trip.dropoffLng()), trip.fare(),
                trip.surgeMultiplier());
    }
}
