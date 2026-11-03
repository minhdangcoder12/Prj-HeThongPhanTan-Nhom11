package com.rideflow.driver.adapter.out.persistence;

import com.rideflow.driver.application.port.out.IdempotencyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class IdempotencyJdbcRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    IdempotencyJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CachedResponse> find(String key, String operation) {
        String sql = "SELECT status_code, response_body::text AS response_body " +
                "FROM idempotency_record WHERE idempotency_key = ? AND operation = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CachedResponse(
                rs.getInt("status_code"),
                rs.getString("response_body")
        ), key, operation).stream().findFirst();
    }

    @Override
    public void save(String key, String operation, int statusCode, String body) {
        String sql = "INSERT INTO idempotency_record (idempotency_key, operation, status_code, response_body) " +
                "VALUES (?, ?, ?, ?::jsonb) ON CONFLICT (idempotency_key) DO NOTHING";
        jdbcTemplate.update(sql, key, operation, statusCode, body);
    }
}

