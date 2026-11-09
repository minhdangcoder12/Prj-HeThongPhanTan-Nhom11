package com.rideflow.trip.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
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
import java.util.Map;
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
    private final InboxGuard inbox = mock(InboxGuard.class);
    private final TripCommands commands = new TripCommands(trips, idempotency, outbox, inbox);
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
        // Commands read with a row lock so two concurrent commands on one trip run one after the other.
        when(trips.findByIdForUpdate(withStatus.id())).thenReturn(Optional.of(withStatus));
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

    // ---- A2: internal assign-driver, called by the matching saga ----

    @Test
    void assignDriverEmitsDriverAssignedAndStoresKey() {
        Trip trip = stored(TripStatus.REQUESTED);
        UUID driver = UUID.randomUUID();

        Trip assigned = commands.assignDriver(trip.id(), driver, "saga-1:assign");

        assertThat(assigned.status()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(assigned.driverId()).isEqualTo(driver);
        EventEnvelope<TripEventPayload> event = appendedEvent();
        assertThat(event.eventType()).isEqualTo("trip.driver-assigned");
        assertThat(event.payload().driverId()).isEqualTo(driver);
        verify(trips).recordTransition(trip.id(), TripStatus.REQUESTED, TripStatus.DRIVER_ASSIGNED, "SYSTEM", event.correlationId());
        verify(idempotency).save("saga-1:assign", TripCommands.ASSIGN, 200, "\"" + trip.id() + "\"");
    }

    @Test
    void assignReplayWithSameKeyChangesNothing() {
        Trip trip = stored(TripStatus.DRIVER_ASSIGNED);
        when(idempotency.find("saga-1:assign", TripCommands.ASSIGN))
                .thenReturn(Optional.of(new IdempotencyRepository.CachedResponse(200, "\"" + trip.id() + "\"")));

        Trip result = commands.assignDriver(trip.id(), trip.driverId(), "saga-1:assign");

        assertThat(result.id()).isEqualTo(trip.id());
        verify(trips, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void assignSameDriverAgainIsANoOpEvenWithNewKey() {
        // The caller that lost the row-lock race sees the winner's result.
        Trip trip = stored(TripStatus.DRIVER_ASSIGNED);

        Trip result = commands.assignDriver(trip.id(), trip.driverId(), "other-key");

        assertThat(result.driverId()).isEqualTo(trip.driverId());
        verify(trips, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void assignAnotherDriverToAnAssignedTripIsRefused() {
        Trip trip = stored(TripStatus.DRIVER_ASSIGNED);

        assertThatThrownBy(() -> commands.assignDriver(trip.id(), UUID.randomUUID(), "k"))
                .isInstanceOf(InvalidTripTransitionException.class);
        verify(outbox, never()).append(any());
    }

    // ---- A2: trip.no-driver from matching-service ----

    private EventEnvelope<Map<String, Object>> noDriver(UUID tripId) {
        return EventEnvelope.of("trip.no-driver", tripId.toString(), "matching-service", null, Map.of("tripId", tripId.toString()));
    }

    @Test
    void noDriverMovesWaitingTripToNoDriverWithoutRepublishing() {
        Trip trip = stored(TripStatus.REQUESTED);
        var event = noDriver(trip.id());
        when(inbox.firstTime(event.eventId(), TripCommands.NO_DRIVER_CONSUMER, "trip.no-driver")).thenReturn(true);

        commands.markNoDriver(event);

        verify(trips).save(org.mockito.ArgumentMatchers.argThat(t -> t.status() == TripStatus.NO_DRIVER));
        verify(trips).recordTransition(trip.id(), TripStatus.REQUESTED, TripStatus.NO_DRIVER, "SYSTEM", event.correlationId());
        verify(outbox, never()).append(any()); // re-emitting trip.no-driver would loop back to this queue
    }

    @Test
    void duplicateNoDriverIsIgnored() {
        Trip trip = stored(TripStatus.REQUESTED);
        var event = noDriver(trip.id());
        when(inbox.firstTime(event.eventId(), TripCommands.NO_DRIVER_CONSUMER, "trip.no-driver")).thenReturn(false);

        commands.markNoDriver(event);

        verify(trips, never()).save(any());
    }

    @Test
    void lateNoDriverForAnAssignedTripIsSkippedNotThrown() {
        Trip trip = stored(TripStatus.DRIVER_ASSIGNED);
        var event = noDriver(trip.id());
        when(inbox.firstTime(event.eventId(), TripCommands.NO_DRIVER_CONSUMER, "trip.no-driver")).thenReturn(true);

        commands.markNoDriver(event); // rule R3: out-of-order -> WARN and skip, no retry storm

        verify(trips, never()).save(any());
    }

    // ---- A3: payment.succeeded / payment.failed from payment-service ----

    /** aggregateId is the paymentId (asyncapi PaymentEnvelope); the trip comes from payload.tripId. */
    private EventEnvelope<Map<String, Object>> payment(String type, UUID tripId) {
        return EventEnvelope.of(type, UUID.randomUUID().toString(), "payment-service", null,
                Map.of("tripId", tripId.toString(), "amount", 31000, "status", type.endsWith("succeeded") ? "SUCCEEDED" : "FAILED"));
    }

    private void firstDelivery(EventEnvelope<?> event) {
        when(inbox.firstTime(event.eventId(), TripCommands.PAYMENT_CONSUMER, event.eventType())).thenReturn(true);
    }

    @Test
    void paymentSucceededMarksCompletedTripPaid() {
        Trip trip = stored(TripStatus.COMPLETED);
        var event = payment("payment.succeeded", trip.id());
        firstDelivery(event);

        commands.applyPayment(event);

        verify(trips).save(org.mockito.ArgumentMatchers.argThat(t -> t.status() == TripStatus.PAID));
        verify(trips).recordTransition(trip.id(), TripStatus.COMPLETED, TripStatus.PAID, "SYSTEM", event.correlationId());
        verify(outbox, never()).append(any());
    }

    @Test
    void paymentFailedMarksTripPaymentFailed() {
        Trip trip = stored(TripStatus.COMPLETED);
        var event = payment("payment.failed", trip.id());
        firstDelivery(event);

        commands.applyPayment(event);

        verify(trips).save(org.mockito.ArgumentMatchers.argThat(t -> t.status() == TripStatus.PAYMENT_FAILED));
    }

    @Test
    void paymentBeforeTripCompletedIsSkipped() {
        Trip trip = stored(TripStatus.IN_PROGRESS);
        var event = payment("payment.succeeded", trip.id());
        firstDelivery(event);

        commands.applyPayment(event); // rule R3 example: payment.succeeded while trip not COMPLETED

        verify(trips, never()).save(any());
    }

    @Test
    void lateFailureAfterPaidDoesNotOverwrite() {
        Trip trip = stored(TripStatus.PAID);
        var event = payment("payment.failed", trip.id());
        firstDelivery(event);

        commands.applyPayment(event);

        verify(trips, never()).save(any());
    }

    @Test
    void duplicatePaymentEventIsIgnored() {
        Trip trip = stored(TripStatus.COMPLETED);
        var event = payment("payment.succeeded", trip.id());
        when(inbox.firstTime(event.eventId(), TripCommands.PAYMENT_CONSUMER, event.eventType())).thenReturn(false);

        commands.applyPayment(event);

        verify(trips, never()).save(any());
    }
}
