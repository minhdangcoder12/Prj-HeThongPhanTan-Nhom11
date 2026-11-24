package com.rideflow.driver.application.port.out;

import com.rideflow.driver.domain.model.Driver;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository {

    List<Driver> findRecent(int limit);

    Optional<Driver> findById(UUID id);

    Driver save(Driver driver);

    boolean atomicReserve(UUID driverId, UUID tripId, Instant reservedUntil);

    boolean atomicRelease(UUID driverId, UUID tripId);

    /** RESERVED cho đúng chuyến -> ON_TRIP; false nếu tài xế không còn ở trạng thái đó. */
    boolean startTrip(UUID driverId, UUID tripId);

    /** ON_TRIP cho đúng chuyến -> AVAILABLE; false nếu tài xế không còn ở trạng thái đó. */
    boolean finishTrip(UUID driverId, UUID tripId);

    List<Driver> findExpiredReservations(Instant now);
}
