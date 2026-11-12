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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
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
    private final Trip trip = Trip.request(passenger, 10.7765, 106.7010, 10.7626, 106.6602, BigDecimal.ONE, Instant.now());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TripController(queries, commands))
                .setControllerAdvice(new TripErrorHandler()).build();
    }

    private static String body(String pickupLat) {
        return "{\"pickup\":{\"lat\":" + pickupLat + ",\"lng\":106.7010},"
                + "\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}";
    }

    private MockHttpServletRequestBuilder createAs(String role) {
        var req = post("/api/trips").contentType(MediaType.APPLICATION_JSON).content(body("10.7765"));
        return role == null ? req : req.header("X-User-Id", passenger.toString()).header("X-User-Role", role);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, String role) {
        return req.header("X-User-Role", role);
    }

    @Test
    void createReturns201WithTrip() throws Exception {
        when(commands.create(eq(passenger), anyDouble(), anyDouble(), anyDouble(), anyDouble(), isNull())).thenReturn(trip);

        mvc.perform(createAs("PASSENGER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(trip.id().toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.fare").value(31000));
    }

    @Test
    void createPassesIdempotencyKey() throws Exception {
        when(commands.create(eq(passenger), anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("k1"))).thenReturn(trip);

        mvc.perform(createAs("PASSENGER").header("Idempotency-Key", "k1"))
                .andExpect(status().isCreated());
    }

    @Test
    void tooLongIdempotencyKeyIs400() throws Exception {
        // Cột idempotency_key là VARCHAR(200): header dài không được kiểm từng gây lỗi 500.
        mvc.perform(createAs("PASSENGER").header("Idempotency-Key", "k".repeat(161)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutIdentityIs401() throws Exception {
        mvc.perform(createAs(null)).andExpect(status().isUnauthorized());
    }

    @Test
    void driverCannotCreateTrip() throws Exception {
        mvc.perform(createAs("DRIVER")).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotAcceptStartCompleteOrReject() throws Exception {
        for (String action : new String[]{"accept", "start", "complete", "reject"}) {
            mvc.perform(as(post("/api/trips/" + trip.id() + "/" + action), "PASSENGER")).andExpect(status().isForbidden());
            mvc.perform(post("/api/trips/" + trip.id() + "/" + action)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void latitudeOutOfRangeIs400() throws Exception {
        mvc.perform(createAs("PASSENGER").content(body("91")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongStatusIs409ProblemDetail() throws Exception {
        when(commands.start(eq(trip.id()), any())).thenThrow(new InvalidTripTransitionException(TripStatus.REQUESTED, TripStatus.IN_PROGRESS));

        mvc.perform(as(post("/api/trips/" + trip.id() + "/start"), "DRIVER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Trip cannot go from REQUESTED to IN_PROGRESS"));
    }

    @Test
    void unknownTripIs404() throws Exception {
        when(commands.accept(any(), any())).thenThrow(new NoSuchElementException("Trip x not found"));

        mvc.perform(as(post("/api/trips/" + UUID.randomUUID() + "/accept"), "DRIVER")).andExpect(status().isNotFound());
    }

    @Test
    void completeAndRejectAreRouted() throws Exception {
        when(commands.complete(eq(trip.id()), any())).thenReturn(trip);
        when(commands.reject(eq(trip.id()), any())).thenReturn(trip);

        mvc.perform(as(post("/api/trips/" + trip.id() + "/complete"), "DRIVER")).andExpect(status().isOk());
        mvc.perform(as(post("/api/trips/" + trip.id() + "/reject"), "DRIVER")).andExpect(status().isOk());
    }
}
