package com.rideflow.trip.application.port.out;

import com.rideflow.trip.domain.model.Trip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {

    List<Trip> findRecent(int limit);

    Optional<Trip> findById(UUID id);

    Trip save(Trip trip);
}
