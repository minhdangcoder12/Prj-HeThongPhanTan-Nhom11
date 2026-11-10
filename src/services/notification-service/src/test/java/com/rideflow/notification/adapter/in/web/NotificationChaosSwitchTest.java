package com.rideflow.notification.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationChaosSwitchTest {

    @Test
    void shouldNeverFailWhenRateIsZero() {
        NotificationChaosSwitch chaos = new NotificationChaosSwitch();
        chaos.setFailRate(0.0);
        for (int i = 0; i < 20; i++) {
            assertThat(chaos.shouldFail()).isFalse();
        }
    }

    @Test
    void shouldAlwaysFailWhenRateIsOne() {
        NotificationChaosSwitch chaos = new NotificationChaosSwitch();
        chaos.setFailRate(1.0);
        for (int i = 0; i < 20; i++) {
            assertThat(chaos.shouldFail()).isTrue();
        }
    }

    @Test
    void shouldClampRateBetweenZeroAndOne() {
        NotificationChaosSwitch chaos = new NotificationChaosSwitch();
        chaos.setFailRate(-0.5);
        assertThat(chaos.getFailRate()).isEqualTo(0.0);

        chaos.setFailRate(1.5);
        assertThat(chaos.getFailRate()).isEqualTo(1.0);
    }
}
