package com.rideflow.matching.adapter.out.persistence;

import com.rideflow.matching.domain.model.MatchingAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matching_attempts")
class MatchingAttemptJpaEntity {

    @Id
    private UUID id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private String result;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchingAttemptJpaEntity() {
    }

    static MatchingAttemptJpaEntity fromDomain(MatchingAttempt attempt) {
        var e = new MatchingAttemptJpaEntity();
        e.id = attempt.id();
        e.sagaId = attempt.sagaId();
        e.driverId = attempt.driverId();
        e.result = attempt.result();
        e.reason = attempt.reason();
        e.createdAt = attempt.createdAt();
        return e;
    }

    MatchingAttempt toDomain() {
        return new MatchingAttempt(id, sagaId, driverId, result, reason, createdAt);
    }
}

