package com.rideflow.trip.domain.model;

public class InvalidTripTransitionException extends RuntimeException {

    public InvalidTripTransitionException(TripStatus from, TripStatus to) {
        super("Trip cannot go from " + from + " to " + to);
    }
}
