package com.rideflow.notification.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.notification.application.port.out.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventsTest {

    private final InboxGuard inbox = mock(InboxGuard.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationEvents events = new NotificationEvents(inbox, repository);
    private final EventEnvelope<Map<String, Object>> tripCreated =
            EventEnvelope.of("trip.created", UUID.randomUUID().toString(), "trip-service", null, Map.of());

    @Test
    void firstDeliveryRecordsNotification() {
        when(inbox.firstTime(tripCreated.eventId(), NotificationEvents.CONSUMER, "trip.created")).thenReturn(true);

        events.handle(tripCreated);

        verify(repository).save(any());
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        when(inbox.firstTime(tripCreated.eventId(), NotificationEvents.CONSUMER, "trip.created")).thenReturn(false);

        events.handle(tripCreated);

        verify(repository, never()).save(any());
    }
}
