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
import org.springframework.transaction.support.TransactionOperations;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingSagaCoordinatorTest {

    private final MatchingSagaRepository repository = mock(MatchingSagaRepository.class);
    private final DriverLocator driverLocator = mock(DriverLocator.class);
    private final DriverServiceClient driverServiceClient = mock(DriverServiceClient.class);
    private final TripServiceClient tripServiceClient = mock(TripServiceClient.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);

    private final MatchingSagaCoordinator coordinator = new MatchingSagaCoordinator(
            repository, driverLocator, driverServiceClient, tripServiceClient, outbox, TransactionOperations.withoutTransaction()
    );

    private final UUID tripId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID causationId = UUID.randomUUID();

    @Test
    void happyPath_SagaCompletesSuccessfully() {
        when(repository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(driverId));
        when(driverServiceClient.reserve(eq(driverId), eq(tripId), anyString())).thenReturn(true);
        when(tripServiceClient.assignDriver(eq(tripId), eq(driverId), anyString())).thenReturn(true);

        MatchingSaga result = coordinator.run(coordinator.start(tripId, causationId), 10.7769, 106.7009, causationId);

        assertThat(result.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(result.driverId()).isEqualTo(driverId);
        verify(driverServiceClient).reserve(eq(driverId), eq(tripId), anyString());
        verify(tripServiceClient).assignDriver(eq(tripId), eq(driverId), anyString());
        verify(repository).recordAttempt(any(MatchingAttempt.class));

        // Verify outbox published saga changed events
        verify(outbox, times(5)).append(any()); // STARTED, CANDIDATE_SELECTED, DRIVER_RESERVED, TRIP_ASSIGNED, COMPLETED
    }

    @Test
    void firstDriverConflict_secondDriverSucceeds() {
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();

        when(repository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(driver1, driver2));

        // driver 1 fails reservation, driver 2 succeeds
        when(driverServiceClient.reserve(eq(driver1), eq(tripId), anyString())).thenReturn(false);
        when(driverServiceClient.reserve(eq(driver2), eq(tripId), anyString())).thenReturn(true);
        when(tripServiceClient.assignDriver(eq(tripId), eq(driver2), anyString())).thenReturn(true);

        MatchingSaga result = coordinator.run(coordinator.start(tripId, causationId), 10.7769, 106.7009, causationId);

        assertThat(result.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(result.driverId()).isEqualTo(driver2);
        verify(repository, times(2)).recordAttempt(any(MatchingAttempt.class));
    }

    @Test
    void allCandidatesFail_sagaFailsAndPublishesNoDriver() {
        UUID driver1 = UUID.randomUUID();

        when(repository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(driver1));
        when(driverServiceClient.reserve(eq(driver1), eq(tripId), anyString())).thenReturn(false);

        MatchingSaga result = coordinator.run(coordinator.start(tripId, causationId), 10.7769, 106.7009, causationId);

        assertThat(result.status()).isEqualTo(SagaStatus.FAILED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox, times(4)).append(captor.capture());

        boolean hasNoDriverEvent = captor.getAllValues().stream()
                .anyMatch(e -> "trip.no-driver".equals(e.eventType()));
        assertThat(hasNoDriverEvent).isTrue();
    }

    @Test
    void assignFails_triggersCompensationAndReleasesDriver() {
        when(repository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(driverId));
        when(driverServiceClient.reserve(eq(driverId), eq(tripId), anyString())).thenReturn(true);
        when(tripServiceClient.assignDriver(eq(tripId), eq(driverId), anyString())).thenReturn(false);

        MatchingSaga result = coordinator.run(coordinator.start(tripId, causationId), 10.7769, 106.7009, causationId);

        assertThat(result.status()).isEqualTo(SagaStatus.COMPENSATED);
        verify(driverServiceClient).release(eq(driverId), eq(tripId), anyString());
        assertThat(appendedTypes()).contains("trip.no-driver"); // trip không kẹt ở REQUESTED
    }

    @Test
    void releaseFailsDuringCompensation_sagaStaysCompensatingForRetry() {
        when(repository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any())).thenReturn(List.of(driverId));
        when(driverServiceClient.reserve(eq(driverId), eq(tripId), anyString())).thenReturn(true);
        when(tripServiceClient.assignDriver(eq(tripId), eq(driverId), anyString())).thenReturn(false);
        doThrow(new IllegalStateException("driver-service down"))
                .when(driverServiceClient).release(eq(driverId), eq(tripId), anyString());

        MatchingSaga result = coordinator.run(coordinator.start(tripId, causationId), 10.7769, 106.7009, causationId);

        assertThat(result.status()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(appendedTypes()).doesNotContain("trip.no-driver");
    }

    @Test
    void recoveryRetriesStuckCompensation_releasesDriverAndReportsNoDriver() {
        MatchingSaga stuck = compensatingSaga(Instant.now());
        when(repository.findByStatus(SagaStatus.COMPENSATING)).thenReturn(List.of(stuck));
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        coordinator.recoverStuckSagas(Duration.ofSeconds(60));

        verify(driverServiceClient).release(eq(driverId), eq(tripId), eq("saga-release-" + tripId + "-" + driverId));
        verify(repository).save(argThat(s -> s.status() == SagaStatus.COMPENSATED));
        assertThat(appendedTypes()).contains("trip.no-driver");
    }

    @Test
    void recoveryGivesUpAfterReservationExpired_sagaFailsAndReportsNoDriver() {
        MatchingSaga stuck = compensatingSaga(Instant.now().minusSeconds(120));
        when(repository.findByStatus(SagaStatus.COMPENSATING)).thenReturn(List.of(stuck));
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        coordinator.recoverStuckSagas(Duration.ofSeconds(60));

        verify(driverServiceClient, never()).release(any(), any(), anyString());
        verify(repository).save(argThat(s -> s.status() == SagaStatus.FAILED));
        assertThat(appendedTypes()).contains("trip.no-driver");
    }

    @Test
    void releaseFailsAfterReject_stillFindsTheNextDriver() {
        UUID next = UUID.randomUUID();
        MatchingSaga searching = MatchingSaga.start(tripId);
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any())).thenReturn(List.of(next));
        when(driverServiceClient.reserve(eq(next), eq(tripId), anyString())).thenReturn(true);
        when(tripServiceClient.assignDriver(eq(tripId), eq(next), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("driver-service down"))
                .when(driverServiceClient).release(eq(driverId), eq(tripId), anyString());

        MatchingSaga result = coordinator.searchAgain(searching, driverId, 10.7769, 106.7009, causationId);

        assertThat(result.status()).isEqualTo(SagaStatus.COMPLETED); // chuyến không kẹt ở MATCHING
        assertThat(result.driverId()).isEqualTo(next);
    }

    @Test
    void recoveryResumesSagaInterruptedAfterReserving_withTheSameKeys() {
        Instant old = Instant.now().minusSeconds(300);
        MatchingSaga stuck = new MatchingSaga(UUID.randomUUID(), tripId, driverId, SagaStatus.DRIVER_RESERVED,
                "DRIVER_RESERVED", 1, null, old, old);
        when(repository.findByStatus(SagaStatus.DRIVER_RESERVED)).thenReturn(List.of(stuck));
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tripServiceClient.assignDriver(eq(tripId), eq(driverId), anyString())).thenReturn(true);

        coordinator.recoverStuckSagas(Duration.ofSeconds(60));

        verify(tripServiceClient).assignDriver(tripId, driverId, "saga-assign-" + tripId + "-" + driverId);
        verify(driverServiceClient, never()).reserve(any(), any(), anyString()); // đã giữ rồi, không giữ thêm ai
        verify(repository).save(argThat(s -> s.status() == SagaStatus.COMPLETED));
    }

    @Test
    void recoveryEndsSagaInterruptedBeforeChoosingADriver_andReportsNoDriver() {
        Instant old = Instant.now().minusSeconds(300);
        MatchingSaga stuck = new MatchingSaga(UUID.randomUUID(), tripId, null, SagaStatus.STARTED, "STARTED", 0, null, old, old);
        when(repository.findByStatus(SagaStatus.STARTED)).thenReturn(List.of(stuck));
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        coordinator.recoverStuckSagas(Duration.ofSeconds(60));

        verify(repository).save(argThat(s -> s.status() == SagaStatus.FAILED));
        assertThat(appendedTypes()).contains("trip.no-driver");
    }

    private MatchingSaga compensatingSaga(Instant updatedAt) {
        return new MatchingSaga(UUID.randomUUID(), tripId, driverId, SagaStatus.COMPENSATING, "COMPENSATING", 1,
                "Assign driver failed", updatedAt, updatedAt);
    }

    private List<String> appendedTypes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox, atLeast(0)).append(captor.capture());
        return captor.getAllValues().stream().map(EventEnvelope::eventType).toList();
    }

    @Test
    void driverRejectsTrip_releasesRejectingDriverAndAssignsNextCandidate() {
        UUID rejectingDriver = UUID.randomUUID();
        UUID nextDriver = UUID.randomUUID();

        MatchingSaga existingSaga = new MatchingSaga(UUID.randomUUID(), tripId, rejectingDriver,
                SagaStatus.COMPLETED, "COMPLETED", 1, null, Instant.now().minusSeconds(10), Instant.now());

        when(repository.findByTripId(tripId)).thenReturn(Optional.of(existingSaga));
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(nextDriver));
        when(driverServiceClient.reserve(eq(nextDriver), eq(tripId), anyString())).thenReturn(true);
        when(tripServiceClient.assignDriver(eq(tripId), eq(nextDriver), anyString())).thenReturn(true);

        MatchingSaga result = coordinator.searchAgain(coordinator.driverRejected(tripId, rejectingDriver, causationId), rejectingDriver, 10.7769, 106.7009, causationId);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(result.driverId()).isEqualTo(nextDriver);

        // Verify releasing the rejecting driver
        verify(driverServiceClient).release(eq(rejectingDriver), eq(tripId), anyString());
        // Verify reserving and assigning the new driver
        verify(driverServiceClient).reserve(eq(nextDriver), eq(tripId), anyString());
        verify(tripServiceClient).assignDriver(eq(tripId), eq(nextDriver), anyString());
        // Verify attempts recorded (1 for reject, 1 for new driver reserved)
        verify(repository, times(2)).recordAttempt(any(MatchingAttempt.class));
    }

    @Test
    void multiInstanceCompetingConsumer_returnsExistingTerminalSagaWithoutDuplicateProcessing() {
        MatchingSaga existingCompletedSaga = new MatchingSaga(UUID.randomUUID(), tripId, driverId,
                SagaStatus.COMPLETED, "COMPLETED", 1, null, Instant.now().minusSeconds(10), Instant.now());

        when(repository.findByTripId(tripId)).thenReturn(Optional.of(existingCompletedSaga));

        MatchingSaga result = coordinator.run(coordinator.start(tripId, causationId), 10.7769, 106.7009, causationId);

        assertThat(result).isSameAs(existingCompletedSaga);
        verify(driverServiceClient, times(0)).reserve(any(), any(), any());
        verify(tripServiceClient, times(0)).assignDriver(any(), any(), any());
        verify(outbox, times(0)).append(any());
    }
}
