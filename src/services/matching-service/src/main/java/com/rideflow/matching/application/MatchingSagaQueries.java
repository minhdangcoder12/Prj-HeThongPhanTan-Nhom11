package com.rideflow.matching.application;

import com.rideflow.matching.application.port.out.MatchingSagaRepository;
import com.rideflow.matching.domain.model.MatchingSaga;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read use cases; also feed the System Monitor. */
@Service
@Transactional(readOnly = true)
public class MatchingSagaQueries {

    private static final int MONITOR_LIMIT = 100;

    private final MatchingSagaRepository repository;

    public MatchingSagaQueries(MatchingSagaRepository repository) {
        this.repository = repository;
    }

    public List<MatchingSaga> recent() {
        return repository.findRecent(MONITOR_LIMIT);
    }

    public Optional<MatchingSaga> byId(UUID id) {
        return repository.findById(id);
    }

    public Optional<MatchingSaga> byTripId(UUID tripId) {
        return repository.findByTripId(tripId);
    }
}
