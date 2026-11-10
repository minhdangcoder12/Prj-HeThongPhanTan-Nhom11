package com.rideflow.matching.adapter.in.web;

import com.rideflow.matching.application.port.out.DriverLocator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalMatchingSupplyControllerTest {

    private final DriverLocator driverLocator = mock(DriverLocator.class);
    private final InternalMatchingSupplyController controller = new InternalMatchingSupplyController(driverLocator);

    @Test
    void shouldReturnSupplyCountFromDriverLocator() {
        when(driverLocator.countAvailable(10.7765, 106.7010, 3.0)).thenReturn(5);

        Map<String, Object> result = controller.supply(10.7765, 106.7010, 3.0);

        assertThat(result).containsEntry("availableDrivers", 5);
        assertThat(result).containsEntry("count", 5);
        assertThat(result).containsEntry("radiusKm", 3.0);
    }
}

