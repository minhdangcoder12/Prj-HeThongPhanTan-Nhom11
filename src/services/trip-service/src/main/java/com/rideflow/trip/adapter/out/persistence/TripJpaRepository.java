package com.rideflow.trip.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {
}
