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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class MatchingSagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MatchingSagaCoordinator.class);

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

        return tryMatchCandidate(saga, pickupLat, pickupLng, causationId);
    }

    public MatchingSaga handleDriverRejected(UUID tripId, UUID driverId, double pickupLat, double pickupLng, UUID causationId) {
        MatchingSaga saga = repository.findByTripId(tripId).orElse(null);
        if (saga == null) {
            return null;
        }

        repository.recordAttempt(MatchingAttempt.of(saga.id(), driverId, "REJECTED", "Driver rejected trip"));

        String releaseIdempotencyKey = "saga-release-reject-" + saga.id() + "-" + driverId;
        driverServiceClient.release(driverId, tripId, releaseIdempotencyKey);

        saga = new MatchingSaga(saga.id(), tripId, null, SagaStatus.STARTED,
                "SEARCHING_AFTER_REJECT", saga.attempts(), null, saga.createdAt(), Instant.now());
        saga = repository.save(saga);
        publishSagaChanged(saga, "Driver " + driverId + " rejected, selecting next candidate", causationId);

        return tryMatchCandidate(saga, pickupLat, pickupLng, causationId);
    }

    private MatchingSaga tryMatchCandidate(MatchingSaga saga, double pickupLat, double pickupLng, UUID causationId) {
        List<UUID> triedDrivers = new ArrayList<>(repository.findAttemptsBySagaId(saga.id()).stream()
                .map(MatchingAttempt::driverId)
                .toList());

        List<UUID> candidates = driverLocator.nearestAvailable(pickupLat, pickupLng, 5, triedDrivers);

        if (candidates.isEmpty()) {
            saga = saga.fail("No available driver candidates found");
            saga = repository.save(saga);
            publishSagaChanged(saga, "No available driver candidates found", causationId);
            publishNoDriver(saga.tripId(), causationId);
            return saga;
        }

        for (UUID candidate : candidates) {
            saga = saga.candidateSelected(candidate);
            saga = repository.save(saga);
            publishSagaChanged(saga, null, causationId);

            String reserveIdempotencyKey = "saga-reserve-" + saga.id() + "-" + candidate;
            boolean reserved = driverServiceClient.reserve(candidate, saga.tripId(), reserveIdempotencyKey);

            if (!reserved) {
                repository.recordAttempt(MatchingAttempt.of(saga.id(), candidate, "RESERVE_FAILED", "Driver conflict or unavailable"));
                continue;
            }

            repository.recordAttempt(MatchingAttempt.of(saga.id(), candidate, "RESERVED", null));
            saga = saga.driverReserved();
            saga = repository.save(saga);
            publishSagaChanged(saga, null, causationId);

            String assignIdempotencyKey = "saga-assign-" + saga.id() + "-" + saga.tripId();
            boolean assigned = tripServiceClient.assignDriver(saga.tripId(), candidate, assignIdempotencyKey);

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
                return compensate(saga, causationId);
            }
        }

        saga = saga.fail("All candidates exhausted");
        saga = repository.save(saga);
        publishSagaChanged(saga, "All candidates exhausted", causationId);
        publishNoDriver(saga.tripId(), causationId);
        return saga;
    }

    /**
     * Bù trừ: nhả tài xế đã giữ rồi báo trip không có tài xế (trip.no-driver), để chuyến không kẹt ở REQUESTED.
     * Nhả lỗi thì saga ở lại COMPENSATING (không nuốt lỗi), SagaRecoveryJob thử lại sau.
     */
    MatchingSaga compensate(MatchingSaga saga, UUID causationId) {
        try {
            driverServiceClient.release(saga.driverId(), saga.tripId(), "saga-release-" + saga.id() + "-" + saga.driverId());
        } catch (RuntimeException e) {
            log.warn("Saga {} could not release driver {} yet, will retry: {}", saga.id(), saga.driverId(), e.getMessage());
            return saga;
        }
        saga = repository.save(saga.compensated());
        publishSagaChanged(saga, "Compensated driver release", causationId);
        publishNoDriver(saga.tripId(), causationId);
        return saga;
    }

    /**
     * Thử lại các saga kẹt ở COMPENSATING (vd driver-service sập lúc bù trừ). Quá {@code giveUpAfter} thì
     * driver-service đã tự nhả tài xế khi hết hạn giữ: saga kết thúc FAILED và vẫn báo trip không có tài xế.
     */
    public void retryCompensations(Duration giveUpAfter) {
        Instant giveUp = Instant.now().minus(giveUpAfter);
        for (MatchingSaga saga : repository.findByStatus(SagaStatus.COMPENSATING)) {
            if (saga.updatedAt().isBefore(giveUp)) {
                MatchingSaga failed = repository.save(saga.fail("Driver release kept failing; reservation expires on its own"));
                publishSagaChanged(failed, failed.lastError(), null);
                publishNoDriver(failed.tripId(), null);
            } else {
                compensate(saga, null);
            }
        }
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
