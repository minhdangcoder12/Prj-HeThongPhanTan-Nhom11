package com.rideflow.trip.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.trip.application.port.out.IdempotencyRepository;
import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.InvalidTripTransitionException;
import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripCommandsTest {

    private final TripRepository trips = mock(TripRepository.class);
    private final IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final TripCommands commands = new TripCommands(trips, idempotency, outbox);
    private final UUID passenger = UUID.randomUUID();

    @BeforeEach
    void saveReturnsArgument() {
        when(trips.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Trip stored(TripStatus status) {
        Trip trip = Trip.request(passenger, 10.7765, 106.7010, 10.7626, 106.6602, Instant.now());
        Trip withStatus = new Trip(trip.id(), passenger, status == TripStatus.REQUESTED ? null : UUID.randomUUID(),
                status, trip.pickupLat(), trip.pickupLng(), trip.dropoffLat(), trip.dropoffLng(), trip.fare(),
                trip.createdAt(), trip.updatedAt());
        when(trips.findById(withStatus.id())).thenReturn(Optional.of(withStatus));
        return withStatus;
    }

    @SuppressWarnings("unchecked")
    private EventEnvelope<TripEventPayload> appendedEvent() {
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        return (EventEnvelope<TripEventPayload>) captor.getValue();
    }

    @Test
    void createSavesTripHistoryAndTripCreatedEvent() {
        Trip trip = commands.create(passenger, 10.7765, 106.7010, 10.7626, 106.6602, null);

        assertThat(trip.status()).isEqualTo(TripStatus.REQUESTED);
        EventEnvelope<TripEventPayload> event = appendedEvent();
        assertThat(event.eventType()).isEqualTo("trip.created");
        assertThat(event.aggregateId()).isEqualTo(trip.id().toString());
        assertThat(event.producer()).isEqualTo("trip-service");
        assertThat(event.payload().tripId()).isEqualTo(trip.id());
        assertThat(event.payload().pickup().lat()).isEqualTo(10.7765);
        assertThat(event.payload().fare()).isEqualByComparingTo(trip.fare());
        verify(trips).recordTransition(trip.id(), null, TripStatus.REQUESTED, "PASSENGER", event.correlationId());
    }

    @Test
    void createWithIdempotencyKeyStoresTheTripId() {
        when(idempotency.find("k1", TripCommands.CREATE)).thenReturn(Optional.empty());

        Trip trip = commands.create(passenger, 10.7765, 106.7010, 10.7626, 106.6602, "k1");

        verify(idempotency).save("k1", TripCommands.CREATE, 201, "\"" + trip.id() + "\"");
    }

    @Test
    void replayedIdempotencyKeyReturnsSameTripWithoutNewEvent() {
        Trip existing = stored(TripStatus.REQUESTED);
        when(idempotency.find("k1", TripCommands.CREATE))
                .thenReturn(Optional.of(new IdempotencyRepository.CachedResponse(201, "\"" + existing.id() + "\"")));

        Trip trip = commands.create(passenger, 10.7765, 106.7010, 10.7626, 106.6602, "k1");

        assertThat(trip.id()).isEqualTo(existing.id());
        verify(trips, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void acceptEmitsTripAccepted() {
        assertThat(commands.accept(stored(TripStatus.DRIVER_ASSIGNED).id()).status()).isEqualTo(TripStatus.ACCEPTED);
        assertThat(appendedEvent().eventType()).isEqualTo("trip.accepted");
    }

    @Test
    void startEmitsTripStarted() {
        commands.start(stored(TripStatus.ACCEPTED).id());
        assertThat(appendedEvent().eventType()).isEqualTo("trip.started");
    }

    @Test
    void completeEmitsTripCompletedAndRecordsHistory() {
        Trip trip = stored(TripStatus.IN_PROGRESS);
        commands.complete(trip.id());
        EventEnvelope<TripEventPayload> event = appendedEvent();
        assertThat(event.eventType()).isEqualTo("trip.completed");
        verify(trips).recordTransition(eq(trip.id()), eq(TripStatus.IN_PROGRESS), eq(TripStatus.COMPLETED),
                eq("DRIVER"), eq(event.correlationId()));
    }

    @Test
    void rejectSendsRejectingDriverAndPickupToMatching() {
        Trip trip = stored(TripStatus.DRIVER_ASSIGNED);

        Trip rejected = commands.reject(trip.id());

        assertThat(rejected.status()).isEqualTo(TripStatus.MATCHING);
        EventEnvelope<TripEventPayload> event = appendedEvent();
        assertThat(event.eventType()).isEqualTo("trip.driver-rejected");
        assertThat(event.payload().driverId()).isEqualTo(trip.driverId());
        assertThat(event.payload().pickup().lng()).isEqualTo(106.7010);
    }

    @Test
    void wrongStatusIsRefusedWithoutSideEffects() {
        UUID id = stored(TripStatus.REQUESTED).id();

        assertThatThrownBy(() -> commands.start(id)).isInstanceOf(InvalidTripTransitionException.class);
        verify(trips, never()).save(any());
        verify(outbox, never()).append(any());
        verify(trips, never()).recordTransition(any(), any(), any(), anyString(), any());
    }

    @Test
    void unknownTripIsNotFound() {
        assertThatThrownBy(() -> commands.accept(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
    }
}
