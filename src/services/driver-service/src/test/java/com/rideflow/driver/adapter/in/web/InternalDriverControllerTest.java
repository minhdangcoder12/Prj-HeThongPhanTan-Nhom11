package com.rideflow.driver.adapter.in.web;

import com.rideflow.driver.application.DriverCommands;
import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalDriverControllerTest {

    private final DriverCommands commands = mock(DriverCommands.class);
    private MockMvc mockMvc;

    private final UUID driverId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        InternalDriverController controller = new InternalDriverController(commands);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void reserveSuccess() throws Exception {
        Driver reservedDriver = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.RESERVED, tripId, Instant.now().plusSeconds(30), 10.7769, 106.7009, Instant.now());

        when(commands.reserveDriver(eq(driverId), eq(tripId), any(Duration.class), eq("idem-key")))
                .thenReturn(reservedDriver);

        mockMvc.perform(post("/internal/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", "idem-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + tripId + "\",\"ttlSeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.tripId").value(tripId.toString()));
    }

    @Test
    void reserveConflict() throws Exception {
        when(commands.reserveDriver(eq(driverId), eq(tripId), any(Duration.class), any()))
                .thenThrow(new IllegalStateException("Driver not available"));

        mockMvc.perform(post("/internal/drivers/" + driverId + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + tripId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reserveNotFound() throws Exception {
        when(commands.reserveDriver(eq(driverId), eq(tripId), any(Duration.class), any()))
                .thenThrow(new NoSuchElementException("Driver not found"));

        mockMvc.perform(post("/internal/drivers/" + driverId + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + tripId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void releaseSuccess() throws Exception {
        Driver availableDriver = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());

        when(commands.releaseDriver(eq(driverId), eq(tripId), eq("idem-rel")))
                .thenReturn(availableDriver);

        mockMvc.perform(post("/internal/drivers/" + driverId + "/release")
                        .header("Idempotency-Key", "idem-rel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + tripId + "\",\"reason\":\"canceled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void releaseNotFound() throws Exception {
        when(commands.releaseDriver(eq(driverId), any(), any()))
                .thenThrow(new NoSuchElementException("Driver not found"));

        mockMvc.perform(post("/internal/drivers/" + driverId + "/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + tripId + "\"}"))
                .andExpect(status().isNotFound());
    }
}

