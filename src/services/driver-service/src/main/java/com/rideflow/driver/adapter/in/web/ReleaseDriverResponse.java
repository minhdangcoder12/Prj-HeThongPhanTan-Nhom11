package com.rideflow.driver.adapter.in.web;

import com.rideflow.driver.domain.model.DriverStatus;

import java.util.UUID;

public record ReleaseDriverResponse(
        UUID driverId,
        DriverStatus status
) {}

