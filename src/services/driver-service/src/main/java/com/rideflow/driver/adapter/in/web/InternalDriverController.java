package com.rideflow.driver.adapter.in.web;

import com.rideflow.driver.application.DriverCommands;
import com.rideflow.driver.domain.model.Driver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/internal/drivers")
public class InternalDriverController {

    private final DriverCommands commands;

    public InternalDriverController(DriverCommands commands) {
        this.commands = commands;
    }

    @PostMapping("/{id}/reserve")
    public ReserveDriverResponse reserve(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReserveDriverRequest request) {
        try {
            Duration ttl = request.ttlSeconds() != null ? Duration.ofSeconds(request.ttlSeconds()) : Duration.ofSeconds(30);
            Driver driver = commands.reserveDriver(id, request.tripId(), ttl, idempotencyKey);
            return new ReserveDriverResponse(driver.id(), driver.status(), driver.currentTripId(), driver.reservedUntil());
        } catch (NoSuchElementException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    @PostMapping("/{id}/release")
    public ReleaseDriverResponse release(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ReleaseDriverRequest request) {
        try {
            UUID tripId = request != null ? request.tripId() : null;
            Driver driver = commands.releaseDriver(id, tripId, idempotencyKey);
            return new ReleaseDriverResponse(driver.id(), driver.status());
        } catch (NoSuchElementException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }
}

