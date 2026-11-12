package com.rideflow.matching.adapter.out.http;

import com.rideflow.matching.application.port.out.DriverServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
public class HttpDriverServiceClient implements DriverServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDriverServiceClient.class);
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public HttpDriverServiceClient(@Value("${rideflow.services.driver:http://localhost:8082}") String driverServiceUrl) {
        this(driverServiceUrl, CircuitBreakerRegistry.ofDefaults());
    }

    @Autowired // 2 constructor: Spring phải được chỉ rõ dùng cái nhận CircuitBreakerRegistry
    public HttpDriverServiceClient(
            @Value("${rideflow.services.driver:http://localhost:8082}") String driverServiceUrl,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(driverServiceUrl)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("driverService");
    }

    @Override
    public boolean reserve(UUID driverId, UUID tripId, String idempotencyKey) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    var response = restClient.post()
                            .uri("/internal/drivers/{id}/reserve", driverId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("tripId", tripId, "ttlSeconds", 30))
                            .retrieve()
                            .toBodilessEntity();
                    return response.getStatusCode().is2xxSuccessful();
                } catch (HttpClientErrorException.Conflict conflict) {
                    log.info("Driver {} is unavailable/conflict: {}", driverId, conflict.getMessage());
                    return false;
                } catch (HttpClientErrorException clientError) {
                    log.warn("Client error reserving driver {}: {}", driverId, clientError.getMessage());
                    return false;
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for driver-service, failing fast reserve for driver {}: {}", driverId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to reserve driver {}: {}", driverId, e.getMessage());
            return false;
        }
    }

    @Override
    public void release(UUID driverId, UUID tripId, String idempotencyKey) {
        try {
            circuitBreaker.executeRunnable(() -> {
                try {
                    restClient.post()
                            .uri("/internal/drivers/{id}/release", driverId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("tripId", tripId, "reason", "saga compensation"))
                            .retrieve()
                            .toBodilessEntity();
                } catch (HttpClientErrorException clientError) {
                    log.warn("Client error releasing driver {}: {}", driverId, clientError.getMessage());
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for driver-service, failing fast release for driver {}: {}", driverId, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to release driver {}: {}", driverId, e.getMessage());
        }
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
