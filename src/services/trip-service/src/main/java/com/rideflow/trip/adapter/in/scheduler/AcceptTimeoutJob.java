package com.rideflow.trip.adapter.in.scheduler;

import com.rideflow.trip.application.TripCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Tài xế không bấm Nhận chuyến trong hạn thì chuyến quay lại tìm tài xế khác (hạn 60 s < hạn giữ tài xế 90 s, đủ thời gian khi thuyết trình). */
@Component
class AcceptTimeoutJob {

    private final TripCommands commands;
    private final Duration acceptTimeout;

    AcceptTimeoutJob(TripCommands commands, @Value("${rideflow.trip.accept-timeout:60s}") Duration acceptTimeout) {
        this.commands = commands;
        this.acceptTimeout = acceptTimeout;
    }

    @Scheduled(fixedDelayString = "${rideflow.trip.accept-timeout-check-ms:5000}")
    void expireOverdueAssignments() {
        commands.expireOverdueAssignments(acceptTimeout);
    }
}
