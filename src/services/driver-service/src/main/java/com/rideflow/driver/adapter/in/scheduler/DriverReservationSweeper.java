package com.rideflow.driver.adapter.in.scheduler;

import com.rideflow.driver.application.DriverCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DriverReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(DriverReservationSweeper.class);
    private final DriverCommands commands;

    public DriverReservationSweeper(DriverCommands commands) {
        this.commands = commands;
    }

    @Scheduled(fixedDelayString = "${rideflow.driver.sweeper-interval-ms:5000}")
    public void sweep() {
        int swept = commands.sweepExpiredReservations();
        if (swept > 0) {
            log.info("Swept and released {} expired driver reservations", swept);
        }
    }
}

