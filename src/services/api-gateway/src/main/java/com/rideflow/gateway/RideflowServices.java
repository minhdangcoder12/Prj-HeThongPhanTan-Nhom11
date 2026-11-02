package com.rideflow.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** Base URL of every business service, keyed by short name (user, driver, trip...). */
@ConfigurationProperties("rideflow")
public record RideflowServices(Map<String, String> services) {
}
