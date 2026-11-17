package com.rideflow.notification.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.notification.application.port.out.NotificationChaosPort;
import com.rideflow.notification.application.port.out.NotificationRepository;
import com.rideflow.notification.domain.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventsTest {

    private final InboxGuard inbox = mock(InboxGuard.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final NotificationChaosPort chaosPort = mock(NotificationChaosPort.class);
    private final NotificationEvents events = new NotificationEvents(inbox, repository, outbox, Optional.of(chaosPort));

    @BeforeEach
    void setUp() {
        when(inbox.firstTime(any(), anyString(), anyString())).thenReturn(true);
        when(chaosPort.shouldFail()).thenReturn(false);
    }

    @Test
    void firstDeliveryRecordsNotificationForPassengerAndDriver() {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "trip.driver-assigned", 1, UUID.randomUUID(), null,
                tripId.toString(), "trip-service", Instant.now(),
                Map.of("tripId", tripId.toString(), "passengerId", passengerId.toString(), "driverId", driverId.toString())
        );

        events.handle(event);

        ArgumentCaptor<Notification> notiCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(notiCaptor.capture());

        List<Notification> saved = notiCaptor.getAllValues();
        assertThat(saved).extracting(Notification::recipientId).containsExactlyInAnyOrder(passengerId, driverId);

        verify(outbox, times(2)).append(any());
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        UUID eventId = UUID.randomUUID();
        when(inbox.firstTime(eventId, NotificationEvents.CONSUMER, "trip.created")).thenReturn(false);

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                eventId, "trip.created", 1, UUID.randomUUID(), null,
                UUID.randomUUID().toString(), "trip-service", Instant.now(),
                Map.of("passengerId", UUID.randomUUID().toString())
        );

        events.handle(event);

        verify(repository, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void chaosFailureThrowsException() {
        when(chaosPort.shouldFail()).thenReturn(true);

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "trip.completed", 1, UUID.randomUUID(), null,
                UUID.randomUUID().toString(), "trip-service", Instant.now(),
                Map.of("passengerId", UUID.randomUUID().toString())
        );

        assertThatThrownBy(() -> events.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated notification failure");

        verify(inbox, never()).firstTime(any(), anyString(), anyString());
        verify(repository, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void recordsNotificationForPassengerOnlyWhenDriverIdNull() {
        UUID passengerId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "trip.created", 1, UUID.randomUUID(), null,
                UUID.randomUUID().toString(), "trip-service", Instant.now(),
                Map.of("passengerId", passengerId.toString())
        );

        events.handle(event);

        ArgumentCaptor<Notification> notiCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(notiCaptor.capture());
        assertThat(notiCaptor.getValue().recipientId()).isEqualTo(passengerId);
        verify(outbox, times(1)).append(any());
    }
}
