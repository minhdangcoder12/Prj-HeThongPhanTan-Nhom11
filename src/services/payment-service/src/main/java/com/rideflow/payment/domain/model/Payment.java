package com.rideflow.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). */
public record Payment(
        UUID id,
        UUID tripId,
        BigDecimal amount,
        PaymentStatus status,
        Integer attempts,
        Instant createdAt,
        Instant updatedAt) {

    public static Payment create(UUID id, UUID tripId, BigDecimal amount, PaymentStatus status) {
        Instant now = Instant.now();
        return new Payment(id, tripId, amount, status, 1, now, now);
    }
}
