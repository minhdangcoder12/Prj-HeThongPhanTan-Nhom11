package com.rideflow.matching.adapter.out.persistence;

import com.rideflow.matching.application.port.out.MatchingSagaRepository;
import com.rideflow.matching.domain.model.MatchingSaga;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class MatchingSagaPersistenceAdapter implements MatchingSagaRepository {

    private final MatchingSagaJpaRepository jpa;

    MatchingSagaPersistenceAdapter(MatchingSagaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<MatchingSaga> findRecent(int limit) {
        return jpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(MatchingSagaJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<MatchingSaga> findById(UUID id) {
        return jpa.findById(id).map(MatchingSagaJpaEntity::toDomain);
    }

    @Override
    public MatchingSaga save(MatchingSaga matchingSaga) {
        return jpa.save(MatchingSagaJpaEntity.fromDomain(matchingSaga)).toDomain();
    }
}
