package com.rideflow.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Domain model: plain Java, no framework (rule R2). Put behaviour here, not in adapters. */
public record Notification(
        UUID id,
        UUID tripId,
        UUID recipientId,
        String type,
        String message,
        NotificationStatus status,
        Integer attempts,
        Instant createdAt) {
}
