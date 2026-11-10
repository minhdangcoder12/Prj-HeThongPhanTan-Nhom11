package com.rideflow.matching.adapter.out.http;

import com.rideflow.matching.application.port.out.TripServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpTripServiceClient implements TripServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTripServiceClient.class);
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public HttpTripServiceClient(@Value("${rideflow.services.trip:http://localhost:8083}") String tripServiceUrl) {
        this(tripServiceUrl, CircuitBreakerRegistry.ofDefaults());
    }

    public HttpTripServiceClient(
            @Value("${rideflow.services.trip:http://localhost:8083}") String tripServiceUrl,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(tripServiceUrl)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tripService");
    }

    @Override
    public boolean assignDriver(UUID tripId, UUID driverId, String idempotencyKey) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    var response = restClient.post()
                            .uri("/internal/trips/{id}/assign-driver", tripId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("driverId", driverId))
                            .retrieve()
                            .toBodilessEntity();
                    return response.getStatusCode().is2xxSuccessful();
                } catch (HttpClientErrorException clientError) {
                    log.warn("Client error assigning driver {} to trip {}: {}", driverId, tripId, clientError.getMessage());
                    return false;
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for trip-service, failing fast assign for trip {}: {}", tripId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to assign driver {} to trip {}: {}", driverId, tripId, e.getMessage());
            return false;
        }
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
