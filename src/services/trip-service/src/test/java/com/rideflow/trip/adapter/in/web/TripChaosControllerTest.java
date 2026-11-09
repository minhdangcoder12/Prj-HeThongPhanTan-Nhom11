package com.rideflow.trip.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripChaosControllerTest {

    private final FailAssignSwitch chaos = new FailAssignSwitch();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TripChaosController(chaos)).build();

    @Test
    void switchStartsOff() {
        assertThat(chaos.isOn()).isFalse();
    }

    @Test
    void putTurnsFailAssignOnAndOff() throws Exception {
        mvc.perform(put("/api/chaos/trip/fail-assign").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failAssign").value(true));
        assertThat(chaos.isOn()).isTrue();

        mvc.perform(put("/api/chaos/trip/fail-assign").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        assertThat(chaos.isOn()).isFalse();
    }

    @Test
    void missingEnabledIs400() throws Exception {
        mvc.perform(put("/api/chaos/trip/fail-assign").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointExistsOnlyInDemoProfile() {
        // Rule R8: chaos endpoints must not exist outside SPRING_PROFILES_ACTIVE=demo.
        assertThat(TripChaosController.class.getAnnotation(Profile.class).value()).containsExactly("demo");
    }
}
