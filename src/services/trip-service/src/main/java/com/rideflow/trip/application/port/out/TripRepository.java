package com.rideflow.trip.application.port.out;

import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {

    List<Trip> findRecent(int limit);

    Optional<Trip> findById(UUID id);

    /** Same as findById but locks the row until the transaction ends; use before changing a trip. */
    Optional<Trip> findByIdForUpdate(UUID id);

    Trip save(Trip trip);

    /** Appends one row to trip_status_history ({@code from} is null for the first status). */
    void recordTransition(UUID tripId, TripStatus from, TripStatus to, String actor, UUID correlationId);
}
