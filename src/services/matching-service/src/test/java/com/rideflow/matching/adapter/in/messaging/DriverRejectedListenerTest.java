package com.rideflow.matching.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.matching.application.MatchingSagaCoordinator;
import org.springframework.transaction.support.TransactionOperations;
import com.rideflow.matching.domain.model.MatchingSaga;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverRejectedListenerTest {

    private final InboxGuard inboxGuard = mock(InboxGuard.class);
    private final MatchingSagaCoordinator coordinator = mock(MatchingSagaCoordinator.class);
    private final DriverRejectedListener listener = new DriverRejectedListener(inboxGuard, coordinator, TransactionOperations.withoutTransaction());

    @Test
    void firstTimeEventCallsCoordinator() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverRejectedListener.DriverRejectedPayload payload = new DriverRejectedListener.DriverRejectedPayload(
                tripId, driverId, new DriverRejectedListener.Point(10.7769, 106.7009)
        );
        EventEnvelope<DriverRejectedListener.DriverRejectedPayload> envelope = new EventEnvelope<>(
                eventId, "trip.driver-rejected", 1, UUID.randomUUID(), null, tripId.toString(), "trip-service",
                java.time.Instant.now(), payload
        );

        when(inboxGuard.firstTime(eq(eventId), eq("matching.driver-rejected"), eq("trip.driver-rejected"))).thenReturn(true);
        MatchingSaga saga = MatchingSaga.start(tripId);
        when(coordinator.driverRejected(tripId, driverId, eventId)).thenReturn(saga);

        listener.onDriverRejected(envelope);

        verify(coordinator).searchAgain(eq(saga), eq(driverId), eq(10.7769), eq(106.7009), eq(eventId));
    }

    @Test
    void duplicateEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverRejectedListener.DriverRejectedPayload payload = new DriverRejectedListener.DriverRejectedPayload(
                tripId, driverId, new DriverRejectedListener.Point(10.7769, 106.7009)
        );
        EventEnvelope<DriverRejectedListener.DriverRejectedPayload> envelope = new EventEnvelope<>(
                eventId, "trip.driver-rejected", 1, UUID.randomUUID(), null, tripId.toString(), "trip-service",
                java.time.Instant.now(), payload
        );

        when(inboxGuard.firstTime(eq(eventId), eq("matching.driver-rejected"), eq("trip.driver-rejected"))).thenReturn(false);

        listener.onDriverRejected(envelope);

        verify(coordinator, never()).driverRejected(any(), any(), any());
        verify(coordinator, never()).searchAgain(any(), any(), anyDouble(), anyDouble(), any());
    }
}

