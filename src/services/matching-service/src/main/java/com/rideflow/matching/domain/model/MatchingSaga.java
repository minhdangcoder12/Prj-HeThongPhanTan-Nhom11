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
}
