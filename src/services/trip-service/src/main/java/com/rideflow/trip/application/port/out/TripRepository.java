package com.rideflow.trip.application.port.out;

import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {

    List<Trip> findRecent(int limit);

    Optional<Trip> findById(UUID id);

    /** Như findById nhưng khoá dòng tới hết transaction; dùng trước khi sửa chuyến. */
    Optional<Trip> findByIdForUpdate(UUID id);

    Trip save(Trip trip);

    /** Ghi 1 dòng trip_status_history ({@code from} = null với trạng thái đầu tiên). */
    void recordTransition(UUID tripId, TripStatus from, TripStatus to, String actor, UUID correlationId);
}
