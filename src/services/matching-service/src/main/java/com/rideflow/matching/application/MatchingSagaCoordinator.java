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
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
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
    private final TransactionOperations tx;

    public MatchingSagaCoordinator(
            MatchingSagaRepository repository,
            DriverLocator driverLocator,
            DriverServiceClient driverServiceClient,
            TripServiceClient tripServiceClient,
            OutboxWriter outbox,
            TransactionOperations tx) {
        this.repository = repository;
        this.driverLocator = driverLocator;
        this.driverServiceClient = driverServiceClient;
        this.tripServiceClient = tripServiceClient;
        this.outbox = outbox;
        this.tx = tx;
    }

    /** Bước đầu, chạy trong transaction của inbox: saga STARTED được lưu cùng lúc event được đánh dấu đã nhận. */
    public MatchingSaga start(UUID tripId, UUID causationId) {
        return repository.findByTripId(tripId).orElseGet(() -> persist(MatchingSaga.start(tripId), null, causationId, false));
    }

    /**
     * Chạy saga ngoài transaction: mỗi bước lưu trạng thái + outbox rồi commit TRƯỚC khi gọi service khác (R5),
     * nên matching sập giữa chừng thì SagaRecoveryJob đi tiếp từ bước đã lưu.
     */
    public MatchingSaga run(MatchingSaga saga, double pickupLat, double pickupLng, UUID causationId) {
        if (saga.status() == SagaStatus.COMPLETED || saga.status() == SagaStatus.FAILED || saga.status() == SagaStatus.COMPENSATED) {
            return saga;
        }
        MatchingSaga resumed = resume(saga, causationId);
        return resumed != null ? resumed : tryMatchCandidate(saga, pickupLat, pickupLng, causationId);
    }

    /** Tài xế từ chối (hoặc hết hạn nhận), chạy trong transaction của inbox: saga quay lại STARTED. */
    public MatchingSaga driverRejected(UUID tripId, UUID driverId, UUID causationId) {
        MatchingSaga saga = repository.findByTripId(tripId).orElse(null);
        if (saga == null) {
            return null;
        }
        repository.recordAttempt(MatchingAttempt.of(saga.id(), driverId, "REJECTED", "Driver rejected trip"));
        MatchingSaga searching = new MatchingSaga(saga.id(), tripId, null, SagaStatus.STARTED,
                "SEARCHING_AFTER_REJECT", saga.attempts(), null, saga.createdAt(), Instant.now());
        return persist(searching, "Driver " + driverId + " rejected, selecting next candidate", causationId, false);
    }

    /**
     * Nhả tài xế vừa từ chối rồi tìm người khác. Nhả lỗi (driver-service sập) không được làm chuyến kẹt:
     * ghi log và vẫn tìm tiếp, vì driver-service tự nhả tài xế khi hết hạn giữ.
     */
    public MatchingSaga searchAgain(MatchingSaga saga, UUID rejectedDriverId, double pickupLat, double pickupLng, UUID causationId) {
        try {
            driverServiceClient.release(rejectedDriverId, saga.tripId(), "saga-release-reject-" + saga.id() + "-" + rejectedDriverId);
        } catch (RuntimeException e) {
            log.warn("Could not release rejecting driver {} for trip {}, its reservation expires on its own: {}",
                    rejectedDriverId, saga.tripId(), e.getMessage());
        }
        return tryMatchCandidate(saga, pickupLat, pickupLng, causationId);
    }

    /** Saga đã có tài xế thì đi tiếp với đúng tài xế đó; null = chưa chọn ai, cần tìm. */
    private MatchingSaga resume(MatchingSaga saga, UUID causationId) {
        return switch (saga.status()) {
            case CANDIDATE_SELECTED, DRIVER_RESERVED -> reserveAndAssign(saga, saga.driverId(), causationId);
            case TRIP_ASSIGNED -> persist(saga.complete(), null, causationId, false);
            case COMPENSATING -> compensate(saga, causationId);
            default -> null;
        };
    }

    private MatchingSaga tryMatchCandidate(MatchingSaga saga, double pickupLat, double pickupLng, UUID causationId) {
        List<UUID> triedDrivers = new ArrayList<>(repository.findAttemptsBySagaId(saga.id()).stream()
                .map(MatchingAttempt::driverId)
                .toList());
        List<UUID> candidates = driverLocator.nearestAvailable(pickupLat, pickupLng, 5, triedDrivers);
        for (UUID candidate : candidates) {
            saga = persist(saga.candidateSelected(candidate), null, causationId, false);
            MatchingSaga done = reserveAndAssign(saga, candidate, causationId);
            if (done != null) {
                return done;
            }
        }
        String reason = candidates.isEmpty() ? "No available driver candidates found" : "All candidates exhausted";
        return persist(saga.fail(reason), reason, causationId, true);
    }

    /**
     * Giữ tài xế rồi gán vào chuyến; null = không giữ được (thử người tiếp theo). Key theo chuyến + tài xế nên
     * chạy lại sau sự cố nhận lại đúng kết quả cũ thay vì giữ thêm một tài xế (R4).
     */
    private MatchingSaga reserveAndAssign(MatchingSaga saga, UUID driverId, UUID causationId) {
        String key = saga.tripId() + "-" + driverId;
        if (saga.status() != SagaStatus.DRIVER_RESERVED) {
            if (!driverServiceClient.reserve(driverId, saga.tripId(), "saga-reserve-" + key)) {
                repository.recordAttempt(MatchingAttempt.of(saga.id(), driverId, "RESERVE_FAILED", "Driver conflict or unavailable"));
                return null;
            }
            repository.recordAttempt(MatchingAttempt.of(saga.id(), driverId, "RESERVED", null));
            saga = persist(saga.driverReserved(), null, causationId, false);
        }
        if (tripServiceClient.assignDriver(saga.tripId(), driverId, "saga-assign-" + key)) {
            saga = persist(saga.tripAssigned(), null, causationId, false);
            return persist(saga.complete(), null, causationId, false);
        }
        // Gán thất bại vĩnh viễn -> COMPENSATING -> nhả tài xế -> COMPENSATED (R5)
        saga = persist(saga.startCompensating("Assign driver failed"), "Assign driver failed", causationId, false);
        return compensate(saga, causationId);
    }

    /**
     * Bù trừ: nhả tài xế đã giữ rồi báo trip không có tài xế (trip.no-driver), để chuyến không kẹt ở REQUESTED.
     * Nhả lỗi thì saga ở lại COMPENSATING (không nuốt lỗi), SagaRecoveryJob thử lại sau.
     */
    MatchingSaga compensate(MatchingSaga saga, UUID causationId) {
        try {
            driverServiceClient.release(saga.driverId(), saga.tripId(), "saga-release-" + saga.tripId() + "-" + saga.driverId());
        } catch (RuntimeException e) {
            log.warn("Saga {} could not release driver {} yet, will retry: {}", saga.id(), saga.driverId(), e.getMessage());
            return saga;
        }
        return persist(saga.compensated(), "Compensated driver release", causationId, true);
    }

    /**
     * Gỡ saga kẹt: COMPENSATING thì thử nhả lại (quá {@code giveUpAfter} thì driver-service đã tự nhả, kết thúc FAILED);
     * saga dừng giữa chừng quá {@code giveUpAfter} (matching sập) thì đi tiếp từ bước đã lưu, hoặc FAILED nếu chưa
     * chọn được tài xế (không còn toạ độ điểm đón để tìm). Mọi nhánh kết thúc đều báo trip để chuyến không kẹt.
     */
    public void recoverStuckSagas(Duration giveUpAfter) {
        Instant giveUp = Instant.now().minus(giveUpAfter);
        for (MatchingSaga saga : repository.findByStatus(SagaStatus.COMPENSATING)) {
            if (saga.updatedAt().isBefore(giveUp)) {
                persist(saga.fail("Driver release kept failing; reservation expires on its own"), "Release kept failing", null, true);
            } else {
                compensate(saga, null);
            }
        }
        for (SagaStatus status : List.of(SagaStatus.STARTED, SagaStatus.CANDIDATE_SELECTED, SagaStatus.DRIVER_RESERVED, SagaStatus.TRIP_ASSIGNED)) {
            for (MatchingSaga saga : repository.findByStatus(status)) {
                if (!saga.updatedAt().isBefore(giveUp)) {
                    continue;
                }
                log.warn("Resuming saga {} stuck in {}", saga.id(), status);
                if (resume(saga, null) == null) {
                    persist(saga.fail("Saga interrupted before a driver was chosen"), "Saga interrupted", null, true);
                }
            }
        }
    }

    /** Lưu trạng thái saga và event của nó trong một transaction ngắn, commit ngay (R3 + R5). */
    private MatchingSaga persist(MatchingSaga saga, String reason, UUID causationId, boolean noDriver) {
        return tx.execute(status -> {
            MatchingSaga saved = repository.save(saga);
            publishSagaChanged(saved, reason, causationId);
            if (noDriver) {
                publishNoDriver(saved.tripId(), causationId);
            }
            return saved;
        });
    }

    private void publishSagaChanged(MatchingSaga saga, String reason, UUID causationId) {
        SagaEventPayload payload = new SagaEventPayload(saga.id(), saga.tripId(), saga.driverId(), saga.status(), reason);
        outbox.append(EventEnvelope.of(EVENT_SAGA_CHANGED, saga.id().toString(), PRODUCER, causationId, payload));
    }

    private void publishNoDriver(UUID tripId, UUID causationId) {
        outbox.append(EventEnvelope.of(EVENT_NO_DRIVER, tripId.toString(), PRODUCER, causationId, Map.of("tripId", tripId)));
    }
}
