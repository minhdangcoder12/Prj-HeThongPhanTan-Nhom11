package com.rideflow.matching.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.matching.application.MatchingSagaCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DriverRejectedListener {

    private static final Logger log = LoggerFactory.getLogger(DriverRejectedListener.class);
    private static final String CONSUMER = "matching.driver-rejected";

    private final InboxGuard inboxGuard;
    private final MatchingSagaCoordinator coordinator;

    public DriverRejectedListener(InboxGuard inboxGuard, MatchingSagaCoordinator coordinator) {
        this.inboxGuard = inboxGuard;
        this.coordinator = coordinator;
    }

    @RabbitListener(queues = MatchingMessagingConfiguration.QUEUE_DRIVER_REJECTED)
    @Transactional
    public void onDriverRejected(EventEnvelope<DriverRejectedPayload> envelope) {
        if (!inboxGuard.firstTime(envelope.eventId(), CONSUMER, envelope.eventType())) {
            log.info("Duplicate event {} ignored by {}", envelope.eventId(), CONSUMER);
            return;
        }

        DriverRejectedPayload payload = envelope.payload();
        if (payload == null || payload.tripId() == null || payload.driverId() == null) {
            log.warn("Invalid driver-rejected payload: {}", payload);
            return;
        }

        double lat = (payload.pickup() != null) ? payload.pickup().lat() : 0.0;
        double lng = (payload.pickup() != null) ? payload.pickup().lng() : 0.0;

        coordinator.handleDriverRejected(payload.tripId(), payload.driverId(), lat, lng, envelope.eventId());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriverRejectedPayload(UUID tripId, UUID driverId, Point pickup) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(double lat, double lng) {}
}

