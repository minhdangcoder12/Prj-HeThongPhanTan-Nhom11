package com.rideflow.trip.application;

import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read use cases; also feed the System Monitor. */
@Service
@Transactional(readOnly = true)
public class TripQueries {

    private static final int MONITOR_LIMIT = 100;

    private final TripRepository repository;

    public TripQueries(TripRepository repository) {
        this.repository = repository;
    }

    public List<Trip> recent() {
        return repository.findRecent(MONITOR_LIMIT);
    }

    public Optional<Trip> byId(UUID id) {
        return repository.findById(id);
    }
}
