package com.rideflow.notification.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.notification.application.port.out.NotificationRepository;
import com.rideflow.notification.domain.model.Notification;
import com.rideflow.notification.domain.model.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationEvents {

    static final String CONSUMER = "notification.trip-events";
    private static final Logger log = LoggerFactory.getLogger(NotificationEvents.class);

    private final InboxGuard inbox;
    private final NotificationRepository repository;

    public NotificationEvents(InboxGuard inbox, NotificationRepository repository) {
        this.inbox = inbox;
        this.repository = repository;
    }

    /** Exactly-once business effect: a redelivered eventId creates no second notification (Flow D). */
    @Transactional
    public void handle(EventEnvelope<Map<String, Object>> event) {
        if (!inbox.firstTime(event.eventId(), CONSUMER, event.eventType())) {
            log.info("Duplicate {} {} ignored", event.eventType(), event.eventId());
            return;
        }
        // TODO(Phase 5 – person 3): real recipient, chaos fail-rate for Flow C.
        repository.save(new Notification(UUID.randomUUID(), tripIdOf(event), null, event.eventType(),
                "Event " + event.eventType() + " for trip " + event.aggregateId(),
                NotificationStatus.SENT, 1, Instant.now()));
        log.info("Notification recorded for {} {}", event.eventType(), event.eventId());
    }

    private static UUID tripIdOf(EventEnvelope<?> event) {
        try {
            return UUID.fromString(event.aggregateId());
        } catch (IllegalArgumentException | NullPointerException notATripId) {
            return null;
        }
    }
}
