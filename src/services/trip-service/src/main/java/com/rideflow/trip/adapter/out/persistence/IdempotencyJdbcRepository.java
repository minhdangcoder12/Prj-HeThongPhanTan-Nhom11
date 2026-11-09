package com.rideflow.trip.adapter.out.persistence;

import com.rideflow.trip.application.port.out.IdempotencyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class IdempotencyJdbcRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbc;

    IdempotencyJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CachedResponse> find(String key, String operation) {
        return jdbc.query("SELECT status_code, response_body::text AS body FROM idempotency_record "
                        + "WHERE idempotency_key = ? AND operation = ?",
                (rs, row) -> new CachedResponse(rs.getInt("status_code"), rs.getString("body")), key, operation)
                .stream().findFirst();
    }

    @Override
    public void save(String key, String operation, int statusCode, String body) {
        // shortcut: plain INSERT, two requests racing on one key -> loser rolls back with 500; add ON CONFLICT + re-read if it matters.
        jdbc.update("INSERT INTO idempotency_record (idempotency_key, operation, status_code, response_body) "
                + "VALUES (?, ?, ?, ?::jsonb)", key, operation, statusCode, body);
    }
}
