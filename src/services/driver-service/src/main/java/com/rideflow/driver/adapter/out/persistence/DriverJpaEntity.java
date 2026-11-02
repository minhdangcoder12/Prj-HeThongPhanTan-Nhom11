package com.rideflow.driver.adapter.out.persistence;

import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drivers")
class DriverJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    private DriverStatus status;

    @Column(name = "current_trip_id")
    private UUID currentTripId;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    private Double lat;

    private Double lng;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DriverJpaEntity() {
    }

    static DriverJpaEntity fromDomain(Driver driver) {
        var e = new DriverJpaEntity();
        e.id = driver.id();
        e.userId = driver.userId();
        e.fullName = driver.fullName();
        e.vehiclePlate = driver.vehiclePlate();
        e.status = driver.status();
        e.currentTripId = driver.currentTripId();
        e.reservedUntil = driver.reservedUntil();
        e.lat = driver.lat();
        e.lng = driver.lng();
        e.updatedAt = driver.updatedAt();
        return e;
    }

    Driver toDomain() {
        return new Driver(id, userId, fullName, vehiclePlate, status, currentTripId, reservedUntil, lat, lng, updatedAt);
    }
}
