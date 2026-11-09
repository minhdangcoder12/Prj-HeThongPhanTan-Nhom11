package com.rideflow.trip.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TripStatus {
    REQUESTED, MATCHING, DRIVER_ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING, PAID, PAYMENT_FAILED, NO_DRIVER, CANCELLED;

    // Single source of truth for the lifecycle; anything not listed is a 409.
    private static final Map<TripStatus, Set<TripStatus>> NEXT = Map.of(
            REQUESTED, EnumSet.of(DRIVER_ASSIGNED, NO_DRIVER),
            MATCHING, EnumSet.of(DRIVER_ASSIGNED, NO_DRIVER),
            DRIVER_ASSIGNED, EnumSet.of(ACCEPTED, MATCHING),
            ACCEPTED, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.of(PAID, PAYMENT_FAILED));

    public boolean canMoveTo(TripStatus next) {
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }
}
