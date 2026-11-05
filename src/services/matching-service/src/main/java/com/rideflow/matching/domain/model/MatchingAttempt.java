package com.rideflow.matching.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MatchingAttempt(
        UUID id,
        UUID sagaId,
        UUID driverId,
        String result,
        String reason,
        Instant createdAt) {

    public static MatchingAttempt of(UUID sagaId, UUID driverId, String result, String reason) {
        return new MatchingAttempt(UUID.randomUUID(), sagaId, driverId, result, reason, Instant.now());
    }
}

