package com.rideflow.matching.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.matching.application.MatchingSagaCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Component
public class DriverRejectedListener {

    private static final Logger log = LoggerFactory.getLogger(DriverRejectedListener.class);
    private static final String CONSUMER = "matching.driver-rejected";

    private final InboxGuard inboxGuard;
    private final MatchingSagaCoordinator coordinator;
    private final TransactionOperations tx;

    public DriverRejectedListener(InboxGuard inboxGuard, MatchingSagaCoordinator coordinator, TransactionOperations tx) {
        this.inboxGuard = inboxGuard;
        this.coordinator = coordinator;
        this.tx = tx;
    }

    @RabbitListener(queues = MatchingMessagingConfiguration.QUEUE_DRIVER_REJECTED)
    public void onDriverRejected(EventEnvelope<DriverRejectedPayload> envelope) {
        DriverRejectedPayload payload = envelope.payload();
        if (payload == null || payload.tripId() == null || payload.driverId() == null) {
            log.warn("Invalid driver-rejected payload: {}", payload);
            return;
        }

        double lat = (payload.pickup() != null) ? payload.pickup().lat() : 0.0;
        double lng = (payload.pickup() != null) ? payload.pickup().lng() : 0.0;

        // Chỉ inbox + saga quay lại STARTED chung một transaction; nhả tài xế và tìm người khác tự commit từng bước (R5).
        var saga = tx.execute(status -> inboxGuard.firstTime(envelope.eventId(), CONSUMER, envelope.eventType())
                ? coordinator.driverRejected(payload.tripId(), payload.driverId(), envelope.eventId()) : null);
        if (saga == null) {
            log.info("Duplicate or unknown-trip event {} ignored by {}", envelope.eventId(), CONSUMER);
            return;
        }
        coordinator.searchAgain(saga, payload.driverId(), lat, lng, envelope.eventId());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriverRejectedPayload(UUID tripId, UUID driverId, Point pickup) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(double lat, double lng) {}
}

