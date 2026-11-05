package com.rideflow.matching.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). Put behaviour here, not in adapters. */
public record MatchingSaga(
        UUID id,
        UUID tripId,
        UUID driverId,
        SagaStatus status,
        String currentStep,
        Integer attempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public static MatchingSaga start(UUID tripId) {
        Instant now = Instant.now();
        return new MatchingSaga(UUID.randomUUID(), tripId, null, SagaStatus.STARTED,
                "STARTED", 0, null, now, now);
    }

    public MatchingSaga candidateSelected(UUID candidateDriverId) {
        return new MatchingSaga(id, tripId, candidateDriverId, SagaStatus.CANDIDATE_SELECTED,
                "CANDIDATE_SELECTED", attempts + 1, null, createdAt, Instant.now());
    }

    public MatchingSaga driverReserved() {
        return new MatchingSaga(id, tripId, driverId, SagaStatus.DRIVER_RESERVED,
                "DRIVER_RESERVED", attempts, null, createdAt, Instant.now());
    }

    public MatchingSaga tripAssigned() {
        return new MatchingSaga(id, tripId, driverId, SagaStatus.TRIP_ASSIGNED,
                "TRIP_ASSIGNED", attempts, null, createdAt, Instant.now());
    }

    public MatchingSaga complete() {
        return new MatchingSaga(id, tripId, driverId, SagaStatus.COMPLETED,
                "COMPLETED", attempts, null, createdAt, Instant.now());
    }

    public MatchingSaga fail(String reason) {
        return new MatchingSaga(id, tripId, driverId, SagaStatus.FAILED,
                "FAILED", attempts, reason, createdAt, Instant.now());
    }

    public MatchingSaga startCompensating(String reason) {
        return new MatchingSaga(id, tripId, driverId, SagaStatus.COMPENSATING,
                "COMPENSATING", attempts, reason, createdAt, Instant.now());
    }

    public MatchingSaga compensated() {
        return new MatchingSaga(id, tripId, driverId, SagaStatus.COMPENSATED,
                "COMPENSATED", attempts, lastError, createdAt, Instant.now());
    }
}
