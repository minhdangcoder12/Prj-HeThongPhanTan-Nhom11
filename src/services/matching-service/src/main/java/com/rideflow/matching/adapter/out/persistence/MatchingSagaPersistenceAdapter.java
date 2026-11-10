package com.rideflow.matching.adapter.out.persistence;

import com.rideflow.matching.application.port.out.MatchingSagaRepository;
import com.rideflow.matching.domain.model.MatchingAttempt;
import com.rideflow.matching.domain.model.MatchingSaga;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class MatchingSagaPersistenceAdapter implements MatchingSagaRepository {

    private final MatchingSagaJpaRepository sagaJpa;
    private final MatchingAttemptJpaRepository attemptJpa;

    MatchingSagaPersistenceAdapter(MatchingSagaJpaRepository sagaJpa, MatchingAttemptJpaRepository attemptJpa) {
        this.sagaJpa = sagaJpa;
        this.attemptJpa = attemptJpa;
    }

    @Override
    public List<MatchingSaga> findRecent(int limit) {
        return sagaJpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(MatchingSagaJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<MatchingSaga> findById(UUID id) {
        return sagaJpa.findById(id).map(MatchingSagaJpaEntity::toDomain);
    }

    @Override
    public Optional<MatchingSaga> findByTripId(UUID tripId) {
        return sagaJpa.findByTripId(tripId).map(MatchingSagaJpaEntity::toDomain);
    }

    @Override
    public MatchingSaga save(MatchingSaga matchingSaga) {
        return sagaJpa.save(MatchingSagaJpaEntity.fromDomain(matchingSaga)).toDomain();
    }

    @Override
    public void recordAttempt(MatchingAttempt attempt) {
        attemptJpa.save(MatchingAttemptJpaEntity.fromDomain(attempt));
    }

    @Override
    public List<MatchingAttempt> findAttemptsBySagaId(UUID sagaId) {
        return attemptJpa.findBySagaIdOrderByCreatedAtAsc(sagaId)
                .stream().map(MatchingAttemptJpaEntity::toDomain).toList();
    }
}
