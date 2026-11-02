package com.rideflow.driver.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.domain.model.Driver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class DriverCommands {

    private static final String PRODUCER = "driver-service";
    private static final String EVENT_AVAILABILITY_CHANGED = "driver.availability-changed";
    private static final String EVENT_LOCATION_UPDATED = "driver.location-updated";

    private final DriverRepository repository;
    private final OutboxWriter outbox;

    public DriverCommands(DriverRepository repository, OutboxWriter outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    public Driver updateAvailability(UUID driverId, boolean available) {
        Driver driver = repository.findById(driverId)
                .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));

        Driver updated = driver.changeAvailability(available);
        Driver saved = repository.save(updated);

        publishEvent(EVENT_AVAILABILITY_CHANGED, saved);
        return saved;
    }

    public Driver updateLocation(UUID driverId, Double lat, Double lng) {
        Driver driver = repository.findById(driverId)
                .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));

        Driver updated = driver.updateLocation(lat, lng);
        Driver saved = repository.save(updated);

        publishEvent(EVENT_LOCATION_UPDATED, saved);
        return saved;
    }

    private void publishEvent(String eventType, Driver driver) {
        DriverEventPayload.Location location = (driver.lat() != null && driver.lng() != null)
                ? new DriverEventPayload.Location(driver.lat(), driver.lng())
                : null;
        DriverEventPayload payload = new DriverEventPayload(
                driver.id(),
                driver.status(),
                driver.currentTripId(),
                location
        );
        EventEnvelope<DriverEventPayload> envelope = EventEnvelope.of(
                eventType,
                driver.id().toString(),
                PRODUCER,
                null,
                payload
        );
        outbox.append(envelope);
    }
}
