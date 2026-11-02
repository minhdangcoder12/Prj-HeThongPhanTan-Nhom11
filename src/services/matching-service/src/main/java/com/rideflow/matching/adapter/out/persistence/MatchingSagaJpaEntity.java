package com.rideflow.matching.adapter.out.persistence;

import com.rideflow.matching.domain.model.MatchingSaga;
import com.rideflow.matching.domain.model.SagaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matching_sagas")
class MatchingSagaJpaEntity {

    @Id
    private UUID id;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @Column(name = "current_step")
    private String currentStep;

    private Integer attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MatchingSagaJpaEntity() {
    }

    static MatchingSagaJpaEntity fromDomain(MatchingSaga matchingSaga) {
        var e = new MatchingSagaJpaEntity();
        e.id = matchingSaga.id();
        e.tripId = matchingSaga.tripId();
        e.driverId = matchingSaga.driverId();
        e.status = matchingSaga.status();
        e.currentStep = matchingSaga.currentStep();
        e.attempts = matchingSaga.attempts();
        e.lastError = matchingSaga.lastError();
        e.createdAt = matchingSaga.createdAt();
        e.updatedAt = matchingSaga.updatedAt();
        return e;
    }

    MatchingSaga toDomain() {
        return new MatchingSaga(id, tripId, driverId, status, currentStep, attempts, lastError, createdAt, updatedAt);
    }
}
