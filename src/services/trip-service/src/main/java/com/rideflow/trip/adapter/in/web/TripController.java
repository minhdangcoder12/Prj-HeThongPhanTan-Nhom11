package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.application.TripQueries;
import com.rideflow.trip.domain.model.Trip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
class TripController {

    /** idempotency_key is VARCHAR(200); the use case prefixes the client key with a 36-char UUID + ':'. */
    static final int MAX_KEY = 160;

    record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                 @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {}

    record CreateTripRequest(@NotNull UUID passengerId, @NotNull @Valid Point pickup, @NotNull @Valid Point dropoff) {}

    private final TripQueries queries;
    private final TripCommands commands;

    TripController(TripQueries queries, TripCommands commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    List<Trip> recent() {
        return queries.recent();
    }

    @GetMapping("/{id}")
    Trip byId(@PathVariable UUID id) {
        return queries.byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Trip create(@Valid @RequestBody CreateTripRequest req,
                @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = MAX_KEY) String idempotencyKey) {
        return commands.create(req.passengerId(), req.pickup().lat(), req.pickup().lng(),
                req.dropoff().lat(), req.dropoff().lng(), idempotencyKey);
    }

    @PostMapping("/{id}/accept")
    Trip accept(@PathVariable UUID id) {
        return commands.accept(id);
    }

    @PostMapping("/{id}/start")
    Trip start(@PathVariable UUID id) {
        return commands.start(id);
    }

    @PostMapping("/{id}/complete")
    Trip complete(@PathVariable UUID id) {
        return commands.complete(id);
    }

    @PostMapping("/{id}/reject")
    Trip reject(@PathVariable UUID id) {
        return commands.reject(id);
    }
}
