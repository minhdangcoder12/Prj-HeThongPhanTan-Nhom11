package com.rideflow.driver.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.application.port.out.IdempotencyRepository;
import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverCommandsTest {

    private final DriverRepository repository = mock(DriverRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final IdempotencyRepository idempotencyRepository = mock(IdempotencyRepository.class);
    private final DriverCommands commands = new DriverCommands(repository, outbox, idempotencyRepository);

    private final UUID driverId = UUID.randomUUID();
    private final Driver driver = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
            DriverStatus.OFFLINE, null, null, 10.7769, 106.7009, Instant.now());

    @Test
    void updateAvailabilitySavesDriverAndAppendsOutboxEvent() {
        when(repository.findById(driverId)).thenReturn(Optional.of(driver));
        when(repository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        Driver result = commands.updateAvailability(driverId, true);

        assertThat(result.status()).isEqualTo(DriverStatus.AVAILABLE);
        verify(repository).save(any(Driver.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<DriverEventPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());

        EventEnvelope<DriverEventPayload> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("driver.availability-changed");
        assertThat(event.aggregateId()).isEqualTo(driverId.toString());
        assertThat(event.producer()).isEqualTo("driver-service");
        assertThat(event.payload().driverId()).isEqualTo(driverId);
        assertThat(event.payload().status()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(event.payload().location().lat()).isEqualTo(10.7769);
        assertThat(event.payload().location().lng()).isEqualTo(106.7009);
    }

    @Test
    void updateAvailabilityDriverNotFoundThrowsException() {
        when(repository.findById(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commands.updateAvailability(driverId, true))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(driverId.toString());
    }

    @Test
    void updateLocationSavesDriverAndAppendsOutboxEvent() {
        when(repository.findById(driverId)).thenReturn(Optional.of(driver));
        when(repository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        Driver result = commands.updateLocation(driverId, 10.7800, 106.7100);

        assertThat(result.lat()).isEqualTo(10.7800);
        assertThat(result.lng()).isEqualTo(106.7100);
        verify(repository).save(any(Driver.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<DriverEventPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());

        EventEnvelope<DriverEventPayload> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("driver.location-updated");
        assertThat(event.aggregateId()).isEqualTo(driverId.toString());
        assertThat(event.payload().driverId()).isEqualTo(driverId);
        assertThat(event.payload().location().lat()).isEqualTo(10.7800);
        assertThat(event.payload().location().lng()).isEqualTo(106.7100);
    }

    @Test
    void updateLocationDriverNotFoundThrowsException() {
        when(repository.findById(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commands.updateLocation(driverId, 10.7800, 106.7100))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(driverId.toString());
    }

    @Test
    void reserveDriverSuccess() {
        UUID tripId = UUID.randomUUID();
        Driver available = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());
        Driver reserved = new Driver(driverId, available.userId(), available.fullName(), available.vehiclePlate(),
                DriverStatus.RESERVED, tripId, Instant.now().plusSeconds(30), 10.7769, 106.7009, Instant.now());

        when(repository.findById(driverId)).thenReturn(Optional.of(available), Optional.of(reserved));
        when(repository.atomicReserve(eq(driverId), eq(tripId), any(Instant.class))).thenReturn(true);

        Driver result = commands.reserveDriver(driverId, tripId, Duration.ofSeconds(30), "key-1");

        assertThat(result.status()).isEqualTo(DriverStatus.RESERVED);
        verify(idempotencyRepository).save(eq("key-1"), eq("reserve"), eq(200), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<DriverEventPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("driver.reserved");
        assertThat(captor.getValue().payload().status()).isEqualTo(DriverStatus.RESERVED);
        assertThat(captor.getValue().payload().tripId()).isEqualTo(tripId);
    }

    @Test
    void reserveDriverConflict() {
        UUID tripId = UUID.randomUUID();
        Driver available = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());

        when(repository.findById(driverId)).thenReturn(Optional.of(available));
        when(repository.atomicReserve(eq(driverId), eq(tripId), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> commands.reserveDriver(driverId, tripId, Duration.ofSeconds(30), "key-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be reserved");

        verify(idempotencyRepository).save(eq("key-2"), eq("reserve"), eq(409), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void reserveDriverIdempotentCachedSuccess() {
        UUID tripId = UUID.randomUUID();
        Driver reserved = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.RESERVED, tripId, Instant.now().plusSeconds(30), 10.7769, 106.7009, Instant.now());

        when(idempotencyRepository.find("key-cached", "reserve"))
                .thenReturn(Optional.of(new IdempotencyRepository.CachedResponse(200, "{}")));
        when(repository.findById(driverId)).thenReturn(Optional.of(reserved));

        Driver result = commands.reserveDriver(driverId, tripId, Duration.ofSeconds(30), "key-cached");

        assertThat(result.status()).isEqualTo(DriverStatus.RESERVED);
        verify(repository, never()).atomicReserve(any(), any(), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void releaseDriverSuccess() {
        UUID tripId = UUID.randomUUID();
        Driver reserved = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.RESERVED, tripId, Instant.now().plusSeconds(30), 10.7769, 106.7009, Instant.now());
        Driver available = new Driver(driverId, reserved.userId(), reserved.fullName(), reserved.vehiclePlate(),
                DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());

        when(repository.findById(driverId)).thenReturn(Optional.of(reserved), Optional.of(available));
        when(repository.atomicRelease(eq(driverId), eq(tripId))).thenReturn(true);

        Driver result = commands.releaseDriver(driverId, tripId, "rel-key");

        assertThat(result.status()).isEqualTo(DriverStatus.AVAILABLE);
        verify(idempotencyRepository).save(eq("rel-key"), eq("release"), eq(200), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<DriverEventPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("driver.released");
        assertThat(captor.getValue().payload().status()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    void sweepExpiredReservationsReleasesDriverAndEmitsEvent() {
        UUID tripId = UUID.randomUUID();
        Driver expiredDriver = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.RESERVED, tripId, Instant.now().minusSeconds(10), 10.7769, 106.7009, Instant.now());
        Driver availableDriver = new Driver(driverId, expiredDriver.userId(), expiredDriver.fullName(), expiredDriver.vehiclePlate(),
                DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());

        when(repository.findExpiredReservations(any(Instant.class))).thenReturn(List.of(expiredDriver));
        when(repository.atomicRelease(eq(driverId), eq(tripId))).thenReturn(true);
        when(repository.findById(driverId)).thenReturn(Optional.of(availableDriver));

        int swept = commands.sweepExpiredReservations();

        assertThat(swept).isEqualTo(1);
        verify(repository).atomicRelease(eq(driverId), eq(tripId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<DriverEventPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("driver.released");
        assertThat(captor.getValue().payload().status()).isEqualTo(DriverStatus.AVAILABLE);
    }
}
