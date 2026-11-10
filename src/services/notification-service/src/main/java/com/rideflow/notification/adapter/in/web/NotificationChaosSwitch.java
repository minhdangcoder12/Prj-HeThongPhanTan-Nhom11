package com.rideflow.notification.adapter.in.web;

import com.rideflow.notification.application.port.out.NotificationChaosPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Switch điều khiển tỉ lệ lỗi cho Flow C retry & DLQ; chỉ kích hoạt khi có profile demo (R8). */
@Component
@Profile("demo")
public class NotificationChaosSwitch implements NotificationChaosPort {

    private final AtomicReference<Double> failRate = new AtomicReference<>(0.0);

    public void setFailRate(double rate) {
        this.failRate.set(Math.max(0.0, Math.min(1.0, rate)));
    }

    public double getFailRate() {
        return this.failRate.get();
    }

    @Override
    public boolean shouldFail() {
        double rate = this.failRate.get();
        return rate > 0.0 && ThreadLocalRandom.current().nextDouble() < rate;
    }
}

