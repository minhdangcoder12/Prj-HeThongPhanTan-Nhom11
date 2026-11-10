package com.rideflow.matching.adapter.in.messaging.projection;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.matching.adapter.out.redis.RedisDriverLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverProjectionListenerTest {

    private final RedisDriverLocator driverLocator = mock(RedisDriverLocator.class);
    private final InboxGuard inbox = mock(InboxGuard.class);
    private final DriverProjectionListener listener = new DriverProjectionListener(driverLocator, inbox);

    @BeforeEach
    void setUp() {
        when(inbox.firstTime(any(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAddDriverToRedisWhenAvailableEventReceived() {
        UUID driverId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "driver.availability-changed", 1, UUID.randomUUID(), null,
                driverId.toString(), "driver-service", Instant.now(),
                Map.of("driverId", driverId.toString(), "status", "AVAILABLE", "location", Map.of("lat", 10.7765, "lng", 106.7010))
        );

        listener.on(event);

        verify(driverLocator).addOrUpdateDriver(eq(driverId), eq(10.7765), eq(106.7010));
    }

    @Test
    void shouldUpdateDriverLocationWhenLocationUpdatedEventReceived() {
        UUID driverId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "driver.location-updated", 1, UUID.randomUUID(), null,
                driverId.toString(), "driver-service", Instant.now(),
                Map.of("driverId", driverId.toString(), "status", "AVAILABLE", "location", Map.of("lat", 10.8000, "lng", 106.7500))
        );

        listener.on(event);

        verify(driverLocator).addOrUpdateDriver(eq(driverId), eq(10.8000), eq(106.7500));
    }

    @Test
    void shouldRemoveDriverFromRedisWhenOfflineOrReserved() {
        UUID driverId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(), "driver.availability-changed", 1, UUID.randomUUID(), null,
                driverId.toString(), "driver-service", Instant.now(),
                Map.of("driverId", driverId.toString(), "status", "RESERVED")
        );

        listener.on(event);

        verify(driverLocator).removeDriver(eq(driverId));
        verify(driverLocator, never()).addOrUpdateDriver(any(), anyDouble(), anyDouble());
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        when(inbox.firstTime(eq(eventId), eq(DriverProjectionListener.CONSUMER), eq("driver.availability-changed"))).thenReturn(false);

        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                eventId, "driver.availability-changed", 1, UUID.randomUUID(), null,
                driverId.toString(), "driver-service", Instant.now(),
                Map.of("driverId", driverId.toString(), "status", "AVAILABLE", "location", Map.of("lat", 10.7765, "lng", 106.7010))
        );

        listener.on(event);

        verify(driverLocator, never()).addOrUpdateDriver(any(), anyDouble(), anyDouble());
        verify(driverLocator, never()).removeDriver(any());
    }
}

