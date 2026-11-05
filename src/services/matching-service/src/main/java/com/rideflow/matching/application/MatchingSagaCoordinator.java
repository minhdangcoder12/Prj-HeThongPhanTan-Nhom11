package com.rideflow.matching.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.matching.application.port.out.DriverLocator;
import com.rideflow.matching.application.port.out.DriverServiceClient;
import com.rideflow.matching.application.port.out.MatchingSagaRepository;
import com.rideflow.matching.application.port.out.TripServiceClient;
import com.rideflow.matching.domain.model.MatchingAttempt;
import com.rideflow.matching.domain.model.MatchingSaga;
import com.rideflow.matching.domain.model.SagaStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class MatchingSagaCoordinator {

    private static final String PRODUCER = "matching-service";
    private static final String EVENT_SAGA_CHANGED = "matching.saga-changed";
    private static final String EVENT_NO_DRIVER = "trip.no-driver";

    private final MatchingSagaRepository repository;
    private final DriverLocator driverLocator;
    private final DriverServiceClient driverServiceClient;
    private final TripServiceClient tripServiceClient;
    private final OutboxWriter outbox;

    public MatchingSagaCoordinator(
            MatchingSagaRepository repository,
            DriverLocator driverLocator,
            DriverServiceClient driverServiceClient,
            TripServiceClient tripServiceClient,
            OutboxWriter outbox) {
        this.repository = repository;
        this.driverLocator = driverLocator;
        this.driverServiceClient = driverServiceClient;
        this.tripServiceClient = tripServiceClient;
        this.outbox = outbox;
    }

    public MatchingSaga startOrResumeSaga(UUID tripId, double pickupLat, double pickupLng, UUID causationId) {
        MatchingSaga saga = repository.findByTripId(tripId).orElseGet(() -> {
            MatchingSaga newSaga = MatchingSaga.start(tripId);
            MatchingSaga saved = repository.save(newSaga);
            publishSagaChanged(saved, null, causationId);
            return saved;
        });

        if (saga.status() == SagaStatus.COMPLETED || saga.status() == SagaStatus.FAILED || saga.status() == SagaStatus.COMPENSATED) {
            return saga;
        }

        List<UUID> triedDrivers = new ArrayList<>(repository.findAttemptsBySagaId(saga.id()).stream()
                .map(MatchingAttempt::driverId)
                .toList());

        List<UUID> candidates = driverLocator.nearestAvailable(pickupLat, pickupLng, 5, triedDrivers);

        if (candidates.isEmpty()) {
            saga = saga.fail("No available driver candidates found");
            saga = repository.save(saga);
            publishSagaChanged(saga, "No available driver candidates found", causationId);
            publishNoDriver(tripId, causationId);
            return saga;
        }

        for (UUID candidate : candidates) {
            saga = saga.candidateSelected(candidate);
            saga = repository.save(saga);
            publishSagaChanged(saga, null, causationId);

            String reserveIdempotencyKey = "saga-reserve-" + saga.id() + "-" + candidate;
            boolean reserved = driverServiceClient.reserve(candidate, tripId, reserveIdempotencyKey);

            if (!reserved) {
                repository.recordAttempt(MatchingAttempt.of(saga.id(), candidate, "RESERVE_FAILED", "Driver conflict or unavailable"));
                continue;
            }

            repository.recordAttempt(MatchingAttempt.of(saga.id(), candidate, "RESERVED", null));
            saga = saga.driverReserved();
            saga = repository.save(saga);
            publishSagaChanged(saga, null, causationId);

            String assignIdempotencyKey = "saga-assign-" + saga.id() + "-" + tripId;
            boolean assigned = tripServiceClient.assignDriver(tripId, candidate, assignIdempotencyKey);

            if (assigned) {
                saga = saga.tripAssigned();
                saga = repository.save(saga);
                publishSagaChanged(saga, null, causationId);

                saga = saga.complete();
                saga = repository.save(saga);
                publishSagaChanged(saga, null, causationId);
                return saga;
            } else {
                // Compensation (Rule R5): assign failed permanently -> COMPENSATING -> release driver -> COMPENSATED
                saga = saga.startCompensating("Assign driver failed");
                saga = repository.save(saga);
                publishSagaChanged(saga, "Assign driver failed", causationId);

                String releaseIdempotencyKey = "saga-release-" + saga.id() + "-" + candidate;
                driverServiceClient.release(candidate, tripId, releaseIdempotencyKey);

                saga = saga.compensated();
                saga = repository.save(saga);
                publishSagaChanged(saga, "Compensated driver release", causationId);
                return saga;
            }
        }

        saga = saga.fail("All candidates exhausted");
        saga = repository.save(saga);
        publishSagaChanged(saga, "All candidates exhausted", causationId);
        publishNoDriver(tripId, causationId);
        return saga;
    }

    private void publishSagaChanged(MatchingSaga saga, String reason, UUID causationId) {
        SagaEventPayload payload = new SagaEventPayload(
                saga.id(),
                saga.tripId(),
                saga.driverId(),
                saga.status(),
                reason
        );
        EventEnvelope<SagaEventPayload> envelope = EventEnvelope.of(
                EVENT_SAGA_CHANGED,
                saga.id().toString(),
                PRODUCER,
                causationId,
                payload
        );
        outbox.append(envelope);
    }

    private void publishNoDriver(UUID tripId, UUID causationId) {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.of(
                EVENT_NO_DRIVER,
                tripId.toString(),
                PRODUCER,
                causationId,
                Map.of("tripId", tripId)
        );
        outbox.append(envelope);
    }
}
