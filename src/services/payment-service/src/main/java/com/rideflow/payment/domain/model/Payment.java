package com.rideflow.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). Put behaviour here, not in adapters. */
public record Payment(
        UUID id,
        UUID tripId,
        BigDecimal amount,
        PaymentStatus status,
        Integer attempts,
        Instant createdAt,
        Instant updatedAt) {
}
