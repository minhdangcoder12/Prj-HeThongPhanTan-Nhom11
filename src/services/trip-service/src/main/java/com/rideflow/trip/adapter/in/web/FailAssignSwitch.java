package com.rideflow.trip.adapter.in.web;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Flow E demo fault: when on, internal assign-driver fails. Only TripChaosController (demo profile) can turn it on. */
@Component
class FailAssignSwitch {

    // shortcut: per-instance flag, with several trip-service instances (B9) toggle each one or move it to Redis.
    private final AtomicBoolean on = new AtomicBoolean(false);

    boolean isOn() {
        return on.get();
    }

    void set(boolean enabled) {
        on.set(enabled);
    }
}
