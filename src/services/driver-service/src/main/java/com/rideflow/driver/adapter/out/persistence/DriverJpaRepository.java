package com.rideflow.driver.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface DriverJpaRepository extends JpaRepository<DriverJpaEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = 'RESERVED', current_trip_id = :tripId, reserved_until = :reservedUntil, updated_at = :now WHERE id = :driverId AND status = 'AVAILABLE'", nativeQuery = true)
    int atomicReserve(@Param("driverId") UUID driverId, @Param("tripId") UUID tripId, @Param("reservedUntil") Instant reservedUntil, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE', current_trip_id = NULL, reserved_until = NULL, updated_at = :now WHERE id = :driverId AND status = 'RESERVED' AND (:tripId IS NULL OR current_trip_id = :tripId)", nativeQuery = true)
    int atomicRelease(@Param("driverId") UUID driverId, @Param("tripId") UUID tripId, @Param("now") Instant now);
}
