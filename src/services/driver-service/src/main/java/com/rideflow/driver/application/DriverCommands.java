package com.rideflow.driver.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.application.port.out.IdempotencyRepository;
import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class DriverCommands {

    private static final String PRODUCER = "driver-service";
    private static final String EVENT_AVAILABILITY_CHANGED = "driver.availability-changed";
    private static final String EVENT_LOCATION_UPDATED = "driver.location-updated";
    private static final String EVENT_DRIVER_RESERVED = "driver.reserved";
    private static final String EVENT_DRIVER_RELEASED = "driver.released";

    private final DriverRepository repository;
    private final OutboxWriter outbox;
    private final IdempotencyRepository idempotencyRepository;

    public DriverCommands(DriverRepository repository, OutboxWriter outbox, IdempotencyRepository idempotencyRepository) {
        this.repository = repository;
        this.outbox = outbox;
        this.idempotencyRepository = idempotencyRepository;
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

    public Driver reserveDriver(UUID driverId, UUID tripId, Duration ttl, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyRepository.find(idempotencyKey, "reserve");
            if (cached.isPresent()) {
                if (cached.get().statusCode() == 200) {
                    return repository.findById(driverId)
                            .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));
                } else {
                    throw new IllegalStateException("Driver " + driverId + " is not available for reservation (cached result)");
                }
            }
        }

        Driver driver = repository.findById(driverId)
                .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));

        Instant reservedUntil = Instant.now().plus(ttl != null ? ttl : Duration.ofSeconds(30));
        boolean reserved = repository.atomicReserve(driverId, tripId, reservedUntil);

        if (!reserved) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyRepository.save(idempotencyKey, "reserve", 409, "{\"error\":\"Driver is not available\"}");
            }
            throw new IllegalStateException("Driver " + driverId + " cannot be reserved (not AVAILABLE)");
        }

        Driver updated = repository.findById(driverId)
                .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));

        publishEvent(EVENT_DRIVER_RESERVED, updated);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRepository.save(idempotencyKey, "reserve", 200,
                    "{\"driverId\":\"" + driverId + "\",\"status\":\"RESERVED\",\"tripId\":\"" + tripId + "\"}");
        }

        return updated;
    }

    public Driver releaseDriver(UUID driverId, UUID tripId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyRepository.find(idempotencyKey, "release");
            if (cached.isPresent()) {
                return repository.findById(driverId)
                        .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));
            }
        }

        Driver driver = repository.findById(driverId)
                .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));

        if (driver.status() == DriverStatus.AVAILABLE) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyRepository.save(idempotencyKey, "release", 200,
                        "{\"driverId\":\"" + driverId + "\",\"status\":\"AVAILABLE\"}");
            }
            return driver;
        }

        boolean released = repository.atomicRelease(driverId, tripId);
        if (!released) {
            throw new IllegalStateException("Driver " + driverId + " cannot be released (status=" + driver.status() + ")");
        }

        Driver updated = repository.findById(driverId)
                .orElseThrow(() -> new NoSuchElementException("Driver " + driverId + " not found"));

        publishEvent(EVENT_DRIVER_RELEASED, updated);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRepository.save(idempotencyKey, "release", 200,
                    "{\"driverId\":\"" + driverId + "\",\"status\":\"AVAILABLE\"}");
        }

        return updated;
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
