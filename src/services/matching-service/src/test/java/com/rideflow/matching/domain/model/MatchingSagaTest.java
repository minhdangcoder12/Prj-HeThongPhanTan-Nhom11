package com.rideflow.matching.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingSagaTest {

    @Test
    void happyPathLifecycle() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        MatchingSaga saga = MatchingSaga.start(tripId);
        assertThat(saga.status()).isEqualTo(SagaStatus.STARTED);
        assertThat(saga.tripId()).isEqualTo(tripId);
        assertThat(saga.attempts()).isEqualTo(0);

        saga = saga.candidateSelected(driverId);
        assertThat(saga.status()).isEqualTo(SagaStatus.CANDIDATE_SELECTED);
        assertThat(saga.driverId()).isEqualTo(driverId);
        assertThat(saga.attempts()).isEqualTo(1);

        saga = saga.driverReserved();
        assertThat(saga.status()).isEqualTo(SagaStatus.DRIVER_RESERVED);

        saga = saga.tripAssigned();
        assertThat(saga.status()).isEqualTo(SagaStatus.TRIP_ASSIGNED);

        saga = saga.complete();
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void failureAndCompensationTransitions() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        MatchingSaga saga = MatchingSaga.start(tripId)
                .candidateSelected(driverId)
                .driverReserved();

        MatchingSaga compensating = saga.startCompensating("Assign error");
        assertThat(compensating.status()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(compensating.lastError()).isEqualTo("Assign error");

        MatchingSaga compensated = compensating.compensated();
        assertThat(compensated.status()).isEqualTo(SagaStatus.COMPENSATED);

        MatchingSaga failed = MatchingSaga.start(tripId).fail("No driver");
        assertThat(failed.status()).isEqualTo(SagaStatus.FAILED);
        assertThat(failed.lastError()).isEqualTo("No driver");
    }
}

