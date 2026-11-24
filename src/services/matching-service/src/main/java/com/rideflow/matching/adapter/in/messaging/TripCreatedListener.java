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
public class TripCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(TripCreatedListener.class);
    private static final String CONSUMER = "matching.trip-created";

    private final InboxGuard inboxGuard;
    private final MatchingSagaCoordinator coordinator;
    private final TransactionOperations tx;

    public TripCreatedListener(InboxGuard inboxGuard, MatchingSagaCoordinator coordinator, TransactionOperations tx) {
        this.inboxGuard = inboxGuard;
        this.coordinator = coordinator;
        this.tx = tx;
    }

    @RabbitListener(queues = MatchingMessagingConfiguration.QUEUE_TRIP_CREATED)
    public void onTripCreated(EventEnvelope<TripCreatedPayload> envelope) {

        TripCreatedPayload payload = envelope.payload();
        double lat = (payload != null && payload.pickup() != null) ? payload.pickup().lat() : 0.0;
        double lng = (payload != null && payload.pickup() != null) ? payload.pickup().lng() : 0.0;
        UUID tripId = (payload != null && payload.tripId() != null) ? payload.tripId() : UUID.fromString(envelope.aggregateId());

        // Chỉ inbox + saga STARTED chung một transaction; các bước sau tự commit từng bước (R5).
        var saga = tx.execute(status -> inboxGuard.firstTime(envelope.eventId(), CONSUMER, envelope.eventType())
                ? coordinator.start(tripId, envelope.eventId()) : null);
        if (saga == null) {
            log.info("Duplicate event {} ignored by {}", envelope.eventId(), CONSUMER);
            return;
        }
        coordinator.run(saga, lat, lng, envelope.eventId());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripCreatedPayload(UUID tripId, UUID passengerId, Point pickup, Point dropoff) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(double lat, double lng) {}
}
