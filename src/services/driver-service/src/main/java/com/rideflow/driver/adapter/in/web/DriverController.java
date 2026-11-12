package com.rideflow.driver.adapter.in.web;

import com.rideflow.driver.application.DriverCommands;
import com.rideflow.driver.application.DriverQueries;
import com.rideflow.driver.domain.model.Driver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
class DriverController {

    private final DriverQueries queries;
    private final DriverCommands commands;

    DriverController(DriverQueries queries, DriverCommands commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    List<Driver> recent() {
        return queries.recent();
    }

    @GetMapping("/{id}")
    Driver byId(@PathVariable UUID id) {
        return queries.byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver " + id + " not found"));
    }

    /** Gateway gắn X-User-Role từ JWT; service vẫn kiểm lại role ở endpoint ghi (R8). */
    /** Role DRIVER chưa đủ: X-Driver-Id (từ JWT) phải là chính tài xế trên đường dẫn. */
    private static void requireDriver(String role, UUID self, UUID target) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Role");
        }
        if (!"DRIVER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role DRIVER");
        }
        if (!target.equals(self)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Drivers can only change their own profile");
        }
    }

    @PutMapping("/{id}/availability")
    Driver updateAvailability(@PathVariable UUID id, @Valid @RequestBody UpdateAvailabilityRequest request,
                              @RequestHeader(name = "X-User-Role", required = false) String role,
                              @RequestHeader(name = "X-Driver-Id", required = false) UUID driverId) {
        requireDriver(role, driverId, id);
        try {
            return commands.updateAvailability(id, request.available());
        } catch (NoSuchElementException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    @PutMapping("/{id}/location")
    Driver updateLocation(@PathVariable UUID id, @Valid @RequestBody UpdateLocationRequest request,
                          @RequestHeader(name = "X-User-Role", required = false) String role,
                              @RequestHeader(name = "X-Driver-Id", required = false) UUID driverId) {
        requireDriver(role, driverId, id);
        try {
            return commands.updateLocation(id, request.lat(), request.lng());
        } catch (NoSuchElementException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        }
    }
}

