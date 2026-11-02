package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.application.TripQueries;
import com.rideflow.trip.domain.model.Trip;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
class TripController {

    private final TripQueries queries;

    TripController(TripQueries queries) {
        this.queries = queries;
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
}
