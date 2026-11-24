package com.rideflow.matching.adapter.out.persistence;

import com.rideflow.matching.domain.model.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MatchingSagaJpaRepository extends JpaRepository<MatchingSagaJpaEntity, UUID> {
    Optional<MatchingSagaJpaEntity> findByTripId(UUID tripId);

    List<MatchingSagaJpaEntity> findByStatus(SagaStatus status);
}
