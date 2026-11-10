package com.rideflow.driver.adapter.in.scheduler;

import com.rideflow.driver.application.DriverCommands;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverReservationSweeperTest {

    @Test
    void sweepCallsCommands() {
        DriverCommands commands = mock(DriverCommands.class);
        when(commands.sweepExpiredReservations()).thenReturn(2);

        DriverReservationSweeper sweeper = new DriverReservationSweeper(commands);
        sweeper.sweep();

        verify(commands).sweepExpiredReservations();
    }
}

