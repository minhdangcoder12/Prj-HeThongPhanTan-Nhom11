package com.rideflow.matching.adapter.out.http;

import com.rideflow.matching.application.port.out.TripServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class HttpTripServiceClient implements TripServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTripServiceClient.class);
    private final RestClient restClient;

    public HttpTripServiceClient(@Value("${rideflow.services.trip:http://localhost:8083}") String tripServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(tripServiceUrl)
                .build();
    }

    @Override
    public boolean assignDriver(UUID tripId, UUID driverId, String idempotencyKey) {
        try {
            var response = restClient.post()
                    .uri("/internal/trips/{id}/assign-driver", tripId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("driverId", driverId))
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to assign driver {} to trip {}: {}", driverId, tripId, e.getMessage());
            return false;
        }
    }
}

