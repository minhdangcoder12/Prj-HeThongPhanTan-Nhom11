package com.rideflow.notification.adapter.out.persistence;

import com.rideflow.notification.domain.model.Notification;
import com.rideflow.notification.domain.model.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
class NotificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "recipient_id")
    private UUID recipientId;

    private String type;

    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private Integer attempts;

    @Column(name = "created_at")
    private Instant createdAt;

    protected NotificationJpaEntity() {
    }

    static NotificationJpaEntity fromDomain(Notification notification) {
        var e = new NotificationJpaEntity();
        e.id = notification.id();
        e.tripId = notification.tripId();
        e.recipientId = notification.recipientId();
        e.type = notification.type();
        e.message = notification.message();
        e.status = notification.status();
        e.attempts = notification.attempts();
        e.createdAt = notification.createdAt();
        return e;
    }

    Notification toDomain() {
        return new Notification(id, tripId, recipientId, type, message, status, attempts, createdAt);
    }
}
