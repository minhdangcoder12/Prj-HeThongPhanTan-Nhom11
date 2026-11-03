package com.rideflow.driver.adapter.in.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

record UpdateLocationRequest(
        @NotNull(message = "lat is required")
        @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
        Double lat,

        @NotNull(message = "lng is required")
        @DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
        Double lng
) {
}
