package com.rideflow.trip.adapter.out.persistence;

import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.Trip;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class TripPersistenceAdapter implements TripRepository {

    private final TripJpaRepository jpa;

    TripPersistenceAdapter(TripJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Trip> findRecent(int limit) {
        return jpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(TripJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Trip> findById(UUID id) {
        return jpa.findById(id).map(TripJpaEntity::toDomain);
    }

    @Override
    public Trip save(Trip trip) {
        return jpa.save(TripJpaEntity.fromDomain(trip)).toDomain();
    }
}
