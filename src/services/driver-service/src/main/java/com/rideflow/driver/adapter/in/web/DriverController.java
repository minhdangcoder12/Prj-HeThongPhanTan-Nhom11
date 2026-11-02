package com.rideflow.driver.adapter.in.web;

import com.rideflow.driver.application.DriverQueries;
import com.rideflow.driver.domain.model.Driver;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
class DriverController {

    private final DriverQueries queries;

    DriverController(DriverQueries queries) {
        this.queries = queries;
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
}
