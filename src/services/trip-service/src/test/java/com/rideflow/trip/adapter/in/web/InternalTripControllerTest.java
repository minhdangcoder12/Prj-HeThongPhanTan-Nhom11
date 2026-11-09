package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.domain.model.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalTripControllerTest {

    private final TripCommands commands = mock(TripCommands.class);
    private final UUID driver = UUID.randomUUID();
    private final Trip trip = Trip.request(UUID.randomUUID(), 10.7765, 106.7010, 10.7626, 106.6602, Instant.now())
            .assignDriver(driver, Instant.now());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalTripController(commands))
                .setControllerAdvice(new TripErrorHandler()).build();
    }

    @Test
    void assignReturns200WithTrip() throws Exception {
        when(commands.assignDriver(trip.id(), driver, "saga-1:assign")).thenReturn(trip);

        mvc.perform(post("/internal/trips/" + trip.id() + "/assign-driver").header("Idempotency-Key", "saga-1:assign")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":\"" + driver + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRIVER_ASSIGNED"))
                .andExpect(jsonPath("$.driverId").value(driver.toString()));
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/internal/trips/" + trip.id() + "/assign-driver")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":\"" + driver + "\"}"))
                .andExpect(status().isBadRequest());
        verify(commands, never()).assignDriver(any(), any(), anyString());
    }

    @Test
    void missingDriverIdIs400() throws Exception {
        mvc.perform(post("/internal/trips/" + trip.id() + "/assign-driver").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
