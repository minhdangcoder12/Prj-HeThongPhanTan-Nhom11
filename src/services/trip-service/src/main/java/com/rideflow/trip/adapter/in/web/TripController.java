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

    /** Cột idempotency_key là VARCHAR(200); use case thêm tiền tố UUID 36 ký tự + ':'. */
    static final int MAX_KEY = 160;

    record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                 @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {}

    record CreateTripRequest(@NotNull @Valid Point pickup, @NotNull @Valid Point dropoff) {}

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

    /** Danh tính do gateway gắn từ JWT (client không giả được); service vẫn kiểm role phía server (R8). */
    private static void requireRole(String actual, String expected) {
        if (actual == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Role");
        }
        if (!expected.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role " + expected);
        }
    }

    private static UUID userId(String header) {
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid X-User-Id");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Trip create(@Valid @RequestBody CreateTripRequest req,
                @RequestHeader(name = "X-User-Role", required = false) String role,
                @RequestHeader(name = "X-User-Id", required = false) String userId,
                @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = MAX_KEY) String idempotencyKey) {
        requireRole(role, "PASSENGER");
        return commands.create(userId(userId), req.pickup().lat(), req.pickup().lng(),
                req.dropoff().lat(), req.dropoff().lng(), idempotencyKey);
    }

    @PostMapping("/{id}/accept")
    Trip accept(@PathVariable UUID id, @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireRole(role, "DRIVER");
        return commands.accept(id);
    }

    @PostMapping("/{id}/start")
    Trip start(@PathVariable UUID id, @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireRole(role, "DRIVER");
        return commands.start(id);
    }

    @PostMapping("/{id}/complete")
    Trip complete(@PathVariable UUID id, @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireRole(role, "DRIVER");
        return commands.complete(id);
    }

    @PostMapping("/{id}/reject")
    Trip reject(@PathVariable UUID id, @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireRole(role, "DRIVER");
        return commands.reject(id);
    }
}
