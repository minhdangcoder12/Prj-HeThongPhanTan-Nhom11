package com.rideflow.matching.adapter.in.web;

import com.rideflow.matching.application.MatchingSagaQueries;
import com.rideflow.matching.domain.model.MatchingSaga;
import com.rideflow.matching.domain.model.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchingSagaControllerTest {

    private final MatchingSagaQueries queries = mock(MatchingSagaQueries.class);
    private MockMvc mockMvc;

    private final UUID sagaId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final MatchingSaga saga = new MatchingSaga(sagaId, tripId, driverId, SagaStatus.COMPLETED,
            "COMPLETED", 1, null, Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        MatchingSagaController controller = new MatchingSagaController(queries);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getRecentSagas() throws Exception {
        when(queries.recent()).thenReturn(List.of(saga));

        mockMvc.perform(get("/api/matching/sagas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sagaId.toString()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void getSagaById() throws Exception {
        when(queries.byId(sagaId)).thenReturn(Optional.of(saga));

        mockMvc.perform(get("/api/matching/sagas/" + sagaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sagaId.toString()))
                .andExpect(jsonPath("$.tripId").value(tripId.toString()));
    }

    @Test
    void getSagaByTripIdFallback() throws Exception {
        when(queries.byId(tripId)).thenReturn(Optional.empty());
        when(queries.byTripId(tripId)).thenReturn(Optional.of(saga));

        mockMvc.perform(get("/api/matching/sagas/" + tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sagaId.toString()))
                .andExpect(jsonPath("$.tripId").value(tripId.toString()));
    }

    @Test
    void getSagaNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(queries.byId(unknownId)).thenReturn(Optional.empty());
        when(queries.byTripId(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/matching/sagas/" + unknownId))
                .andExpect(status().isNotFound());
    }
}
