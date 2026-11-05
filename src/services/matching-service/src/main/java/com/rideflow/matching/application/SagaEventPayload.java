package com.rideflow.matching.application;

import com.rideflow.matching.domain.model.SagaStatus;

import java.util.UUID;

public record SagaEventPayload(
        UUID sagaId,
        UUID tripId,
        UUID driverId,
        SagaStatus status,
        String reason
) {}

