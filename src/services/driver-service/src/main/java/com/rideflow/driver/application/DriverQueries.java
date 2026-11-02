package com.rideflow.driver.application;

import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.domain.model.Driver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read use cases; also feed the System Monitor. */
@Service
@Transactional(readOnly = true)
public class DriverQueries {

    private static final int MONITOR_LIMIT = 100;

    private final DriverRepository repository;

    public DriverQueries(DriverRepository repository) {
        this.repository = repository;
    }

    public List<Driver> recent() {
        return repository.findRecent(MONITOR_LIMIT);
    }

    public Optional<Driver> byId(UUID id) {
        return repository.findById(id);
    }
}
