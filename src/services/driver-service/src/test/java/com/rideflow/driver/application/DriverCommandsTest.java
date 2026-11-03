package com.rideflow.driver.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverCommandsTest {

    private final DriverRepository repository = mock(DriverRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final DriverCommands commands = new DriverCommands(repository, outbox);

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
}
