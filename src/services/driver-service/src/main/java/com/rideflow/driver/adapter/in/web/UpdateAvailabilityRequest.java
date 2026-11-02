package com.rideflow.driver.adapter.in.web;

import jakarta.validation.constraints.NotNull;

record UpdateAvailabilityRequest(
        @NotNull(message = "available is required")
        Boolean available
) {
}
