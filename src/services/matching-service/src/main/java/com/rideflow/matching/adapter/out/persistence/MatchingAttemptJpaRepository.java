package com.rideflow.matching.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MatchingAttemptJpaRepository extends JpaRepository<MatchingAttemptJpaEntity, UUID> {
    List<MatchingAttemptJpaEntity> findBySagaIdOrderByCreatedAtAsc(UUID sagaId);
}

