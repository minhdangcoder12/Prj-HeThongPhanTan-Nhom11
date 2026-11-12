package com.rideflow.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    private final Instant now = Instant.parse("2026-11-09T10:00:00Z");
    private final UUID passenger = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();

    private Trip requested() {
        return Trip.request(passenger, 10.7765, 106.7010, 10.7626, 106.6602, BigDecimal.ONE, now);
    }

    @Test
    void requestStartsAsRequestedWithFare() {
        Trip trip = requested();

        assertThat(trip.status()).isEqualTo(TripStatus.REQUESTED);
        assertThat(trip.passengerId()).isEqualTo(passenger);
        assertThat(trip.driverId()).isNull();
        assertThat(trip.fare()).isEqualByComparingTo(new BigDecimal("31000"));
        assertThat(trip.createdAt()).isEqualTo(now);
    }

    @Test
    void assignDriverSetsDriverAndStatus() {
        Trip assigned = requested().assignDriver(driver, now.plusSeconds(5));

        assertThat(assigned.status()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(assigned.driverId()).isEqualTo(driver);
        assertThat(assigned.updatedAt()).isEqualTo(now.plusSeconds(5));
    }

    @Test
    void rejectGoesBackToMatchingWithoutDriver() {
        Trip rejected = requested().assignDriver(driver, now).rejectByDriver(now);

        assertThat(rejected.status()).isEqualTo(TripStatus.MATCHING);
        assertThat(rejected.driverId()).isNull();
    }

    @Test
    void illegalTransitionIsRefused() {
        assertThatThrownBy(() -> requested().transitionTo(TripStatus.COMPLETED, now))
                .isInstanceOf(InvalidTripTransitionException.class)
                .hasMessageContaining("REQUESTED")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void cannotAssignTwice() {
        Trip assigned = requested().assignDriver(driver, now);

        assertThatThrownBy(() -> assigned.assignDriver(UUID.randomUUID(), now))
                .isInstanceOf(InvalidTripTransitionException.class);
    }
}
