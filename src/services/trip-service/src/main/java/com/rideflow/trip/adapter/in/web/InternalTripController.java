package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.domain.model.Trip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** Service-to-service API. The gateway never routes /internal/** (rule R1). */
@RestController
@RequestMapping("/internal/trips")
class InternalTripController {

    record AssignDriverRequest(@NotNull UUID driverId) {}

    private final TripCommands commands;
    private final FailAssignSwitch failAssign;

    InternalTripController(TripCommands commands, FailAssignSwitch failAssign) {
        this.commands = commands;
        this.failAssign = failAssign;
    }

    /** Called by the matching saga; retried with the same Idempotency-Key (rule R4), so the key is mandatory. */
    @PostMapping("/{id}/assign-driver")
    Trip assignDriver(@PathVariable UUID id, @Valid @RequestBody AssignDriverRequest req,
                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (failAssign.isOn()) {
            // Fails before any change, so every saga retry fails too -> saga must compensate (Flow E).
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chaos: assign-driver disabled for Flow E demo");
        }
        return commands.assignDriver(id, req.driverId(), idempotencyKey);
    }
}
