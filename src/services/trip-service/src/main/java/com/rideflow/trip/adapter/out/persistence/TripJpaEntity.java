package com.rideflow.trip.adapter.out.persistence;

import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
class TripJpaEntity {

    @Id
    private UUID id;

    @Column(name = "passenger_id")
    private UUID passengerId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lng")
    private Double pickupLng;

    @Column(name = "dropoff_lat")
    private Double dropoffLat;

    @Column(name = "dropoff_lng")
    private Double dropoffLng;

    private BigDecimal fare;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected TripJpaEntity() {
    }

    static TripJpaEntity fromDomain(Trip trip) {
        var e = new TripJpaEntity();
        e.id = trip.id();
        e.passengerId = trip.passengerId();
        e.driverId = trip.driverId();
        e.status = trip.status();
        e.pickupLat = trip.pickupLat();
        e.pickupLng = trip.pickupLng();
        e.dropoffLat = trip.dropoffLat();
        e.dropoffLng = trip.dropoffLng();
        e.fare = trip.fare();
        e.createdAt = trip.createdAt();
        e.updatedAt = trip.updatedAt();
        return e;
    }

    Trip toDomain() {
        return new Trip(id, passengerId, driverId, status, pickupLat, pickupLng, dropoffLat, dropoffLng, fare, createdAt, updatedAt);
    }
}
