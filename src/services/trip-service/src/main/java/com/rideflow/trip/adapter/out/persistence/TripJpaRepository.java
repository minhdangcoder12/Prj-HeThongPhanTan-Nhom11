package com.rideflow.trip.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rideflow.trip.domain.model.TripStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {

    @Query("select t.id from TripJpaEntity t where t.status = :status and t.updatedAt < :before")
    List<UUID> findIdsByStatusBefore(@Param("status") TripStatus status, @Param("before") Instant before);

    /** SELECT ... FOR UPDATE: các lệnh cùng một chuyến phải xếp hàng trong DB. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TripJpaEntity t where t.id = :id")
    Optional<TripJpaEntity> findForUpdate(@Param("id") UUID id);
}
