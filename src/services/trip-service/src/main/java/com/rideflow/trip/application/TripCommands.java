package com.rideflow.trip.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.trip.application.port.out.IdempotencyRepository;
import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Write use cases. Status, history row and outbox event commit in ONE transaction (rule R3, no dual write). */
@Service
@Transactional
public class TripCommands {

    static final String CREATE = "trip.create";
    private static final String PRODUCER = "trip-service";
    private static final String PASSENGER = "PASSENGER";
    private static final String DRIVER = "DRIVER";

    private final TripRepository trips;
    private final IdempotencyRepository idempotency;
    private final OutboxWriter outbox;

    public TripCommands(TripRepository trips, IdempotencyRepository idempotency, OutboxWriter outbox) {
        this.trips = trips;
        this.idempotency = idempotency;
        this.outbox = outbox;
    }

    /** A retried request with the same {@code idempotencyKey} gets the first trip back, no second trip. */
    public Trip create(UUID passengerId, double pickupLat, double pickupLng, double dropoffLat, double dropoffLng,
                       String idempotencyKey) {
        if (idempotencyKey != null) {
            var cached = idempotency.find(idempotencyKey, CREATE);
            if (cached.isPresent()) {
                return load(UUID.fromString(cached.get().body().replace("\"", "")));
            }
        }
        Trip trip = trips.save(Trip.request(passengerId, pickupLat, pickupLng, dropoffLat, dropoffLng, Instant.now()));
        publish(trip, null, "trip.created", PASSENGER, null);
        if (idempotencyKey != null) {
            idempotency.save(idempotencyKey, CREATE, 201, "\"" + trip.id() + "\"");
        }
        return trip;
    }

    public Trip accept(UUID id) {
        return move(id, "trip.accepted", t -> t.transitionTo(TripStatus.ACCEPTED, Instant.now()));
    }

    public Trip start(UUID id) {
        return move(id, "trip.started", t -> t.transitionTo(TripStatus.IN_PROGRESS, Instant.now()));
    }

    public Trip complete(UUID id) {
        return move(id, "trip.completed", t -> t.transitionTo(TripStatus.COMPLETED, Instant.now()));
    }

    /** matching-service listens to trip.driver-rejected and needs the rejecting driver + pickup to pick the next one. */
    public Trip reject(UUID id) {
        Trip before = load(id);
        Trip after = trips.save(before.rejectByDriver(Instant.now()));
        publish(after, before.status(), "trip.driver-rejected", DRIVER, before.driverId());
        return after;
    }

    private Trip move(UUID id, String eventType, UnaryOperator<Trip> change) {
        Trip before = load(id);
        Trip after = trips.save(change.apply(before));
        publish(after, before.status(), eventType, DRIVER, after.driverId());
        return after;
    }

    private Trip load(UUID id) {
        return trips.findById(id).orElseThrow(() -> new NoSuchElementException("Trip " + id + " not found"));
    }

    private void publish(Trip trip, TripStatus from, String eventType, String actor, UUID driverId) {
        var event = EventEnvelope.of(eventType, trip.id().toString(), PRODUCER, null, TripEventPayload.of(trip, driverId));
        outbox.append(event);
        trips.recordTransition(trip.id(), from, trip.status(), actor, event.correlationId());
    }
}
