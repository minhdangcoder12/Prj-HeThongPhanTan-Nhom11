package com.rideflow.trip.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Chaos endpoint for Flow E (saga compensation). Exists only with SPRING_PROFILES_ACTIVE=demo (rule R8). */
@Profile("demo")
@RestController
@RequestMapping("/api/chaos/trip")
class TripChaosController {

    private static final Logger log = LoggerFactory.getLogger(TripChaosController.class);

    record FailAssignRequest(@NotNull Boolean enabled) {}

    private final FailAssignSwitch failAssign;

    TripChaosController(FailAssignSwitch failAssign) {
        this.failAssign = failAssign;
    }

    @PutMapping("/fail-assign")
    Map<String, Boolean> setFailAssign(@Valid @RequestBody FailAssignRequest req) {
        failAssign.set(req.enabled());
        log.warn("CHAOS: internal assign-driver will {}", req.enabled() ? "FAIL (503)" : "work normally");
        return Map.of("failAssign", req.enabled());
    }
}
