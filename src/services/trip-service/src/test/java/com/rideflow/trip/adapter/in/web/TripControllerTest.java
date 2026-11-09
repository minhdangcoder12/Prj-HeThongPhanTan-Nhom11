package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.application.TripQueries;
import com.rideflow.trip.domain.model.InvalidTripTransitionException;
import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripControllerTest {

    private final TripQueries queries = mock(TripQueries.class);
    private final TripCommands commands = mock(TripCommands.class);
    private final UUID passenger = UUID.randomUUID();
    private final Trip trip = Trip.request(passenger, 10.7765, 106.7010, 10.7626, 106.6602, Instant.now());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TripController(queries, commands))
                .setControllerAdvice(new TripErrorHandler()).build();
    }

    private static String body(String passengerId, String pickupLat) {
        return "{\"passengerId\":" + passengerId + ",\"pickup\":{\"lat\":" + pickupLat + ",\"lng\":106.7010},"
                + "\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}";
    }

    @Test
    void createReturns201WithTrip() throws Exception {
        when(commands.create(eq(passenger), anyDouble(), anyDouble(), anyDouble(), anyDouble(), isNull())).thenReturn(trip);

        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content(body("\"" + passenger + "\"", "10.7765")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(trip.id().toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.fare").value(31000));
    }

    @Test
    void createPassesIdempotencyKey() throws Exception {
        when(commands.create(eq(passenger), anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("k1"))).thenReturn(trip);

        mvc.perform(post("/api/trips").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"" + passenger + "\"", "10.7765")))
                .andExpect(status().isCreated());
    }

    @Test
    void missingPassengerIs400() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content(body("null", "10.7765")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void latitudeOutOfRangeIs400() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content(body("\"" + passenger + "\"", "91")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongStatusIs409ProblemDetail() throws Exception {
        when(commands.start(trip.id())).thenThrow(new InvalidTripTransitionException(TripStatus.REQUESTED, TripStatus.IN_PROGRESS));

        mvc.perform(post("/api/trips/" + trip.id() + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Trip cannot go from REQUESTED to IN_PROGRESS"));
    }

    @Test
    void unknownTripIs404() throws Exception {
        when(commands.accept(any())).thenThrow(new NoSuchElementException("Trip x not found"));

        mvc.perform(post("/api/trips/" + UUID.randomUUID() + "/accept")).andExpect(status().isNotFound());
    }

    @Test
    void completeAndRejectAreRouted() throws Exception {
        when(commands.complete(trip.id())).thenReturn(trip);
        when(commands.reject(trip.id())).thenReturn(trip);

        mvc.perform(post("/api/trips/" + trip.id() + "/complete")).andExpect(status().isOk());
        mvc.perform(post("/api/trips/" + trip.id() + "/reject")).andExpect(status().isOk());
    }
}
