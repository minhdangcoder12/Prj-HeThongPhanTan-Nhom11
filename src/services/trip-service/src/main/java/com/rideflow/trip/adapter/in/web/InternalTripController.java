package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.domain.model.Trip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** API nội bộ giữa các service; gateway không route /internal/** (R1). */
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

    /** Saga gọi và retry với cùng Idempotency-Key (R4), nên key là bắt buộc. */
    @PostMapping("/{id}/assign-driver")
    Trip assignDriver(@PathVariable UUID id, @Valid @RequestBody AssignDriverRequest req,
                      @RequestHeader("Idempotency-Key") @Size(max = TripController.MAX_KEY) String idempotencyKey) {
        if (failAssign.isOn()) {
            // Lỗi trước khi đổi gì nên mọi lần retry đều lỗi -> saga phải bù trừ (Flow E).
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chaos: assign-driver disabled for Flow E demo");
        }
        return commands.assignDriver(id, req.driverId(), idempotencyKey);
    }
}
