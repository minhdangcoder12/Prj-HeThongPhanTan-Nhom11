package com.rideflow.matching.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface MatchingSagaJpaRepository extends JpaRepository<MatchingSagaJpaEntity, UUID> {
}
