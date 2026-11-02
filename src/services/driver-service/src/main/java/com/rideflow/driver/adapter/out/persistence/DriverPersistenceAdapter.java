package com.rideflow.driver.adapter.out.persistence;

import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.domain.model.Driver;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class DriverPersistenceAdapter implements DriverRepository {

    private final DriverJpaRepository jpa;

    DriverPersistenceAdapter(DriverJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Driver> findRecent(int limit) {
        return jpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "fullName")))
                .map(DriverJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Driver> findById(UUID id) {
        return jpa.findById(id).map(DriverJpaEntity::toDomain);
    }

    @Override
    public Driver save(Driver driver) {
        return jpa.save(DriverJpaEntity.fromDomain(driver)).toDomain();
    }
}
