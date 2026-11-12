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
    public int countWaitingNear(double lat, double lng, double radiusKm) {
        // Haversine ngay trong SQL (Postgres không có PostGIS); least(1, ..) tránh asin(>1) do sai số làm tròn.
        return jdbc.queryForObject("SELECT count(*) FROM trips WHERE status IN ('REQUESTED', 'MATCHING') "
                + "AND 2 * 6371 * asin(least(1, sqrt(power(sin(radians(pickup_lat - ?) / 2), 2) "
                + "+ cos(radians(?)) * cos(radians(pickup_lat)) * power(sin(radians(pickup_lng - ?) / 2), 2)))) <= ?",
                Integer.class, lat, lat, lng, radiusKm);
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
    public Optional<Trip> findByIdForUpdate(UUID id) {
        return jpa.findForUpdate(id).map(TripJpaEntity::toDomain);
    }

    @Override
    public Trip save(Trip trip) {
        // Flush ngay: lệnh ghi lịch sử bằng JDBC ngay sau đó cần dòng trips đã có (khoá ngoại).
        return jpa.saveAndFlush(TripJpaEntity.fromDomain(trip)).toDomain();
    }
}
