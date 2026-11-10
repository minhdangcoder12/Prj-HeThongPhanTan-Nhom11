package com.rideflow.driver.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReserveDriverRequest(
        @NotNull UUID tripId,
        Long ttlSeconds
) {}

