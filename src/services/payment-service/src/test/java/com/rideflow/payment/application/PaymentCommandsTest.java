package com.rideflow.payment.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.payment.application.port.out.PaymentGatewayPort;
import com.rideflow.payment.application.port.out.PaymentRepository;
import com.rideflow.payment.domain.model.Payment;
import com.rideflow.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCommandsTest {

    private final InboxGuard inbox = mock(InboxGuard.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final PaymentCommands commands = new PaymentCommands(inbox, outbox, repository, gateway);

    @BeforeEach
    void setup() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inbox.firstTime(any(), anyString(), anyString())).thenReturn(true);
        when(repository.findByTripId(any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldProcessPaymentSuccessfullyAndWriteOutboxEvent() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        BigDecimal fare = BigDecimal.valueOf(50000);

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                eventId, "trip.completed", 1, correlationId, null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("tripId", tripId.toString(), "fare", fare)
        );

        when(gateway.process(any(), eq(tripId), eq(fare))).thenReturn(PaymentStatus.SUCCEEDED);

        commands.handleTripCompleted(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.tripId()).isEqualTo(tripId);
        assertThat(saved.amount()).isEqualTo(fare);
        assertThat(saved.status()).isEqualTo(PaymentStatus.SUCCEEDED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<PaymentEventPayload>> outboxCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(outboxCaptor.capture());
        EventEnvelope<PaymentEventPayload> published = outboxCaptor.getValue();
        assertThat(published.eventType()).isEqualTo("payment.succeeded");
        assertThat(published.correlationId()).isEqualTo(correlationId);
        assertThat(published.causationId()).isEqualTo(eventId);
        assertThat(published.producer()).isEqualTo("payment-service");
        assertThat(published.payload().tripId()).isEqualTo(tripId);
        assertThat(published.payload().amount()).isEqualTo(fare);
        assertThat(published.payload().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void shouldHandlePaymentFailureAndWriteFailedOutboxEvent() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        BigDecimal fare = BigDecimal.valueOf(35000);

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                eventId, "trip.completed", 1, correlationId, null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("tripId", tripId.toString(), "fare", fare)
        );

        when(gateway.process(any(), eq(tripId), eq(fare))).thenReturn(PaymentStatus.FAILED);

        commands.handleTripCompleted(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().status()).isEqualTo(PaymentStatus.FAILED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<PaymentEventPayload>> outboxCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(outboxCaptor.capture());
        EventEnvelope<PaymentEventPayload> published = outboxCaptor.getValue();
        assertThat(published.eventType()).isEqualTo("payment.failed");
        assertThat(published.payload().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldIgnoreDuplicateTripCompletedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        when(inbox.firstTime(eq(eventId), eq(PaymentCommands.CONSUMER), eq("trip.completed"))).thenReturn(false);

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                eventId, "trip.completed", 1, UUID.randomUUID(), null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("tripId", tripId.toString(), "fare", 50000)
        );

        commands.handleTripCompleted(event);

        verify(gateway, never()).process(any(), any(), any());
        verify(repository, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void shouldNotProcessIfPaymentAlreadyExistsForTrip() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        Payment existing = Payment.create(UUID.randomUUID(), tripId, BigDecimal.valueOf(40000), PaymentStatus.SUCCEEDED);
        when(repository.findByTripId(tripId)).thenReturn(Optional.of(existing));

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                eventId, "trip.completed", 1, UUID.randomUUID(), null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("tripId", tripId.toString(), "fare", 40000)
        );

        commands.handleTripCompleted(event);

        verify(gateway, never()).process(any(), any(), any());
        verify(repository, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void shouldFallbackToAggregateIdWhenPayloadTripIdMissing() {
        UUID tripId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "trip.completed", 1, UUID.randomUUID(), null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("fare", 25000)
        );

        when(gateway.process(any(), eq(tripId), any())).thenReturn(PaymentStatus.SUCCEEDED);

        commands.handleTripCompleted(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().tripId()).isEqualTo(tripId);
    }

    @Test
    void shouldFallbackDefaultFareWhenPayloadFareMissing() {
        UUID tripId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "trip.completed", 1, UUID.randomUUID(), null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("tripId", tripId.toString())
        );

        when(gateway.process(any(), eq(tripId), eq(BigDecimal.valueOf(30000)))).thenReturn(PaymentStatus.SUCCEEDED);

        commands.handleTripCompleted(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().amount()).isEqualTo(BigDecimal.valueOf(30000));
    }
}

