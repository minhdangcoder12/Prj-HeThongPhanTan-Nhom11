package com.rideflow.matching.adapter.in.web;

import com.rideflow.matching.application.MatchingSagaQueries;
import com.rideflow.matching.domain.model.MatchingSaga;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matching/sagas")
class MatchingSagaController {

    private final MatchingSagaQueries queries;

    MatchingSagaController(MatchingSagaQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    List<MatchingSaga> recent() {
        return queries.recent();
    }

    @GetMapping("/{id}")
    MatchingSaga byId(@PathVariable UUID id) {
        return queries.byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MatchingSaga " + id + " not found"));
    }
}
