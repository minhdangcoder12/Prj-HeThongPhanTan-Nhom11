package com.rideflow.user.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). Put behaviour here, not in adapters. */
public record User(
        UUID id,
        String email,
        String fullName,
        Role role,
        Instant createdAt) {
}
