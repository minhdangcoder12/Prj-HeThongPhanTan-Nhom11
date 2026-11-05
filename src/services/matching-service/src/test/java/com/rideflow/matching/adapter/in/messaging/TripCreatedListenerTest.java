package com.rideflow.matching.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.matching.application.MatchingSagaCoordinator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripCreatedListenerTest {

    private final InboxGuard inboxGuard = mock(InboxGuard.class);
    private final MatchingSagaCoordinator coordinator = mock(MatchingSagaCoordinator.class);
    private final TripCreatedListener listener = new TripCreatedListener(inboxGuard, coordinator);

    @Test
    void firstTimeEventProcessesSaga() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        TripCreatedListener.TripCreatedPayload payload = new TripCreatedListener.TripCreatedPayload(
                tripId, UUID.randomUUID(), new TripCreatedListener.Point(10.7769, 106.7009), null
        );
        EventEnvelope<TripCreatedListener.TripCreatedPayload> envelope = new EventEnvelope<>(
                eventId, "trip.created", 1, UUID.randomUUID(), causationId, tripId.toString(), "trip-service",
                java.time.Instant.now(), payload
        );

        when(inboxGuard.firstTime(eq(eventId), eq("matching.trip-created"), eq("trip.created"))).thenReturn(true);

        listener.onTripCreated(envelope);

        verify(coordinator).startOrResumeSaga(eq(tripId), eq(10.7769), eq(106.7009), eq(eventId));
    }

    @Test
    void duplicateEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        TripCreatedListener.TripCreatedPayload payload = new TripCreatedListener.TripCreatedPayload(
                tripId, UUID.randomUUID(), new TripCreatedListener.Point(10.7769, 106.7009), null
        );
        EventEnvelope<TripCreatedListener.TripCreatedPayload> envelope = new EventEnvelope<>(
                eventId, "trip.created", 1, UUID.randomUUID(), null, tripId.toString(), "trip-service",
                java.time.Instant.now(), payload
        );

        when(inboxGuard.firstTime(eq(eventId), eq("matching.trip-created"), eq("trip.created"))).thenReturn(false);

        listener.onTripCreated(envelope);

        verify(coordinator, never()).startOrResumeSaga(any(), anyDouble(), anyDouble(), any());
    }
}
