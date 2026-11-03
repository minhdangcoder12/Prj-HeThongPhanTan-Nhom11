package com.rideflow.driver.adapter.in.web;

import java.util.UUID;

public record ReleaseDriverRequest(
        UUID tripId,
        String reason
) {}

