package com.rideflow.driver.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.driver.application.DriverCommands;
import com.rideflow.driver.application.DriverQueries;
import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DriverControllerTest {

    private final DriverQueries queries = mock(DriverQueries.class);
    private final DriverCommands commands = mock(DriverCommands.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private final UUID driverId = UUID.randomUUID();
    private final Driver driver = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
            DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());

    @BeforeEach
    void setUp() {
        DriverController controller = new DriverController(queries, commands);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void putAvailabilitySuccess() throws Exception {
        when(commands.updateAvailability(eq(driverId), eq(true))).thenReturn(driver);

        mockMvc.perform(put("/api/drivers/" + driverId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(driverId.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void putAvailabilityNotFound() throws Exception {
        when(commands.updateAvailability(eq(driverId), anyBoolean()))
                .thenThrow(new NoSuchElementException("Driver not found"));

        mockMvc.perform(put("/api/drivers/" + driverId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void putAvailabilityConflict() throws Exception {
        when(commands.updateAvailability(eq(driverId), anyBoolean()))
                .thenThrow(new IllegalStateException("Cannot change availability"));

        mockMvc.perform(put("/api/drivers/" + driverId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void putLocationSuccess() throws Exception {
        when(commands.updateLocation(eq(driverId), eq(10.7769), eq(106.7009))).thenReturn(driver);

        mockMvc.perform(put("/api/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\": 10.7769, \"lng\": 106.7009}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(10.7769))
                .andExpect(jsonPath("$.lng").value(106.7009));
    }

    @Test
    void putLocationNotFound() throws Exception {
        when(commands.updateLocation(eq(driverId), anyDouble(), anyDouble()))
                .thenThrow(new NoSuchElementException("Driver not found"));

        mockMvc.perform(put("/api/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\": 10.7769, \"lng\": 106.7009}"))
                .andExpect(status().isNotFound());
    }
}
