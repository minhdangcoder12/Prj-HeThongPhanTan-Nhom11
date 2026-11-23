package com.rideflow.matching.adapter.out.http;

import com.rideflow.common.web.CorrelationIdFilter;
import com.rideflow.matching.application.port.out.TripServiceClient;
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
public class HttpTripServiceClient implements TripServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTripServiceClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public HttpTripServiceClient(@Value("${rideflow.services.trip:http://localhost:8083}") String tripServiceUrl) {
        this(tripServiceUrl, CircuitBreakerRegistry.ofDefaults());
    }

    @Autowired // 2 constructor: Spring phải được chỉ rõ dùng cái nhận CircuitBreakerRegistry
    public HttpTripServiceClient(
            @Value("${rideflow.services.trip:http://localhost:8083}") String tripServiceUrl,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        var requestFactory = new SimpleClientHttpRequestFactory(); // timeout theo R4
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(CorrelationIdFilter.forwardOnCalls())
                .baseUrl(tripServiceUrl)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tripService");
    }

    /** false = gán thất bại vĩnh viễn (4xx, hoặc lỗi sau 3 lần thử): saga phải bù trừ. */
    @Override
    public boolean assignDriver(UUID tripId, UUID driverId, String idempotencyKey) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return BoundedRetry.call(() -> restClient.post()
                            .uri("/internal/trips/{id}/assign-driver", tripId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("driverId", driverId))
                            .retrieve()
                            .toBodilessEntity()).getStatusCode().is2xxSuccessful();
                } catch (HttpClientErrorException clientError) {
                    log.warn("Trip {} rejected driver {}: {}", tripId, driverId, clientError.getStatusCode());
                    return false;
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for trip-service, failing fast assign for trip {}", tripId);
            return false;
        } catch (RuntimeException e) {
            log.warn("Failed to assign driver {} to trip {} after {} attempts: {}", driverId, tripId,
                    BoundedRetry.MAX_ATTEMPTS, e.getMessage());
            return false;
        }
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
