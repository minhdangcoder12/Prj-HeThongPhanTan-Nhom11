package com.rideflow.trip.adapter.in.web;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Lỗi giả cho Flow E: bật thì assign-driver trả 503. Chỉ bật được qua TripChaosController (profile demo). */
@Component
class FailAssignSwitch {

    // shortcut: cờ riêng từng instance; chạy nhiều trip-service (B9) thì bật từng cái hoặc chuyển sang Redis.
    private final AtomicBoolean on = new AtomicBoolean(false);

    boolean isOn() {
        return on.get();
    }

    void set(boolean enabled) {
        on.set(enabled);
    }
}
