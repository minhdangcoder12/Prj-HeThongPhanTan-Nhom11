package com.rideflow.trip.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static com.rideflow.trip.domain.model.TripStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class TripStatusTest {

    /** The only legal moves; every other pair must be refused. */
    private static final Set<String> ALLOWED = Set.of(
            "REQUESTED>DRIVER_ASSIGNED", "REQUESTED>NO_DRIVER",
            "MATCHING>DRIVER_ASSIGNED", "MATCHING>NO_DRIVER",
            "DRIVER_ASSIGNED>ACCEPTED", "DRIVER_ASSIGNED>MATCHING",
            "ACCEPTED>IN_PROGRESS",
            "IN_PROGRESS>COMPLETED",
            "COMPLETED>PAID", "COMPLETED>PAYMENT_FAILED");

    static Stream<Arguments> everyPair() {
        return Arrays.stream(values()).flatMap(from -> Arrays.stream(values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void onlyListedTransitionsAreAllowed(TripStatus from, TripStatus to) {
        assertThat(from.canMoveTo(to)).isEqualTo(ALLOWED.contains(from + ">" + to));
    }

    @ParameterizedTest
    @MethodSource("terminal")
    void terminalStatusesGoNowhere(TripStatus status) {
        assertThat(Arrays.stream(values()).noneMatch(status::canMoveTo)).isTrue();
    }

    static Stream<TripStatus> terminal() {
        return Stream.of(PAID, PAYMENT_FAILED, NO_DRIVER, CANCELLED);
    }
}
