package com.rideflow.trip.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {

    /** SELECT ... FOR UPDATE: concurrent commands on one trip queue up in the database. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TripJpaEntity t where t.id = :id")
    Optional<TripJpaEntity> findForUpdate(@Param("id") UUID id);
}
