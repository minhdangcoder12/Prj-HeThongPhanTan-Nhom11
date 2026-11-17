package com.rideflow.notification.application;

import com.rideflow.notification.domain.model.NotificationStatus;

import java.util.UUID;

/** Payload của event notification.*, theo NotificationEnvelope trong contracts/asyncapi/asyncapi.yaml. */
public record NotificationEventPayload(
        UUID notificationId,
        UUID tripId,
        UUID recipientId,
        String eventType,
        NotificationStatus status) {
}

