package com.rideflow.trip.application;

import java.util.UUID;

public class NotTripDriverException extends RuntimeException {

    public NotTripDriverException(UUID tripId) {
        super("Only the driver assigned to trip " + tripId + " can do this");
    }
}
