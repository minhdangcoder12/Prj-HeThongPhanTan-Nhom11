package com.rideflow.trip.adapter.out.persistence;

import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class TripPersistenceAdapter implements TripRepository {

    private final TripJpaRepository jpa;
    private final JdbcTemplate jdbc;

    TripPersistenceAdapter(TripJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public void recordTransition(UUID tripId, TripStatus from, TripStatus to, String actor, UUID correlationId) {
        jdbc.update("INSERT INTO trip_status_history (id, trip_id, from_status, to_status, actor, correlation_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tripId, from == null ? null : from.name(), to.name(), actor, correlationId);
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
        // Flush now: the JDBC history insert right after this needs the trips row (FK trip_status_history.trip_id).
        return jpa.saveAndFlush(TripJpaEntity.fromDomain(trip)).toDomain();
    }
}
