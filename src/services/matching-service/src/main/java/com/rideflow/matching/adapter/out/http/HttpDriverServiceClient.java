package com.rideflow.matching.adapter.out.http;

import com.rideflow.matching.application.port.out.DriverServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class HttpDriverServiceClient implements DriverServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDriverServiceClient.class);
    private final RestClient restClient;

    public HttpDriverServiceClient(@Value("${rideflow.services.driver:http://localhost:8082}") String driverServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(driverServiceUrl)
                .build();
    }

    @Override
    public boolean reserve(UUID driverId, UUID tripId, String idempotencyKey) {
        try {
            var response = restClient.post()
                    .uri("/internal/drivers/{id}/reserve", driverId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("tripId", tripId, "ttlSeconds", 30))
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to reserve driver {}: {}", driverId, e.getMessage());
            return false;
        }
    }

    @Override
    public void release(UUID driverId, UUID tripId, String idempotencyKey) {
        try {
            restClient.post()
                    .uri("/internal/drivers/{id}/release", driverId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("tripId", tripId, "reason", "saga compensation"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to release driver {}: {}", driverId, e.getMessage());
        }
    }
}

