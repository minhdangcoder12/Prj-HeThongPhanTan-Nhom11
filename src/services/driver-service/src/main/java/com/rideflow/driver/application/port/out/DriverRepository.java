package com.rideflow.driver.application.port.out;

import com.rideflow.driver.domain.model.Driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository {

    List<Driver> findRecent(int limit);

    Optional<Driver> findById(UUID id);

    Driver save(Driver driver);
}
