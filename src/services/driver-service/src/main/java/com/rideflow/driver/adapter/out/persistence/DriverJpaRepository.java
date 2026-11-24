package com.rideflow.driver.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface DriverJpaRepository extends JpaRepository<DriverJpaEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = 'RESERVED', current_trip_id = :tripId, reserved_until = :reservedUntil, updated_at = :now WHERE id = :driverId AND status = 'AVAILABLE'", nativeQuery = true)
    int atomicReserve(@Param("driverId") UUID driverId, @Param("tripId") UUID tripId, @Param("reservedUntil") Instant reservedUntil, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE', current_trip_id = NULL, reserved_until = NULL, updated_at = :now WHERE id = :driverId AND status = 'RESERVED' AND (:tripId IS NULL OR current_trip_id = :tripId)", nativeQuery = true)
    int atomicRelease(@Param("driverId") UUID driverId, @Param("tripId") UUID tripId, @Param("now") Instant now);

    // Chỉ đổi khi tài xế còn đúng trạng thái và đúng chuyến: event đến muộn hay sai thứ tự không làm hỏng dữ liệu.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = 'ON_TRIP', reserved_until = NULL, updated_at = :now WHERE id = :driverId AND status = 'RESERVED' AND current_trip_id = :tripId", nativeQuery = true)
    int startTrip(@Param("driverId") UUID driverId, @Param("tripId") UUID tripId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE', current_trip_id = NULL, updated_at = :now WHERE id = :driverId AND status = 'ON_TRIP' AND current_trip_id = :tripId", nativeQuery = true)
    int finishTrip(@Param("driverId") UUID driverId, @Param("tripId") UUID tripId, @Param("now") Instant now);

    @Query("SELECT d FROM DriverJpaEntity d WHERE d.status = com.rideflow.driver.domain.model.DriverStatus.RESERVED AND d.reservedUntil < :now")
    List<DriverJpaEntity> findExpiredReservations(@Param("now") Instant now);
}
