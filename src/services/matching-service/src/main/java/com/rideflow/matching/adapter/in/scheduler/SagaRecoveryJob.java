package com.rideflow.matching.adapter.in.scheduler;

import com.rideflow.matching.application.MatchingSagaCoordinator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Gỡ saga kẹt: thử lại bù trừ, hoặc đi tiếp saga bị dừng giữa chừng khi matching sập (R5). Quá 120 s thì kết thúc. */
@Component
class SagaRecoveryJob {

    private final MatchingSagaCoordinator coordinator;
    private final Duration giveUpAfter;

    SagaRecoveryJob(MatchingSagaCoordinator coordinator,
                    @Value("${rideflow.matching.compensation-give-up:120s}") Duration giveUpAfter) {
        this.coordinator = coordinator;
        this.giveUpAfter = giveUpAfter;
    }

    @Scheduled(fixedDelayString = "${rideflow.matching.recovery-interval-ms:5000}")
    void recoverStuckSagas() {
        coordinator.recoverStuckSagas(giveUpAfter);
    }
}
