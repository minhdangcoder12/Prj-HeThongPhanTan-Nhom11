package com.rideflow.matching.adapter.out.http;

import com.rideflow.common.web.CorrelationIdFilter;
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
        var requestFactory = new SimpleClientHttpRequestFactory(); // timeout theo R4
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(CorrelationIdFilter.forwardOnCalls())
                .baseUrl(driverServiceUrl)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("driverService");
    }

    /** false = không giữ được (tài xế đã bận, hoặc driver-service lỗi hết lượt thử): saga chọn người khác. */
    @Override
    public boolean reserve(UUID driverId, UUID tripId, String idempotencyKey) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return BoundedRetry.call(() -> restClient.post()
                            .uri("/internal/drivers/{id}/reserve", driverId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("tripId", tripId, "ttlSeconds", 90))
                            .retrieve()
                            .toBodilessEntity()).getStatusCode().is2xxSuccessful();
                } catch (HttpClientErrorException clientError) {
                    log.info("Driver {} not reserved: {}", driverId, clientError.getStatusCode());
                    return false;
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for driver-service, failing fast reserve for driver {}", driverId);
            return false;
        } catch (RuntimeException e) {
            log.warn("Failed to reserve driver {} after {} attempts: {}", driverId, BoundedRetry.MAX_ATTEMPTS, e.getMessage());
            return false;
        }
    }

    /**
     * Ném lỗi khi không nhả được (không nuốt lỗi): saga ở lại COMPENSATING để SagaRecoveryJob thử lại.
     * 4xx nghĩa là tài xế không còn giữ cho chuyến này (đã nhả rồi) nên coi như xong.
     */
    @Override
    public void release(UUID driverId, UUID tripId, String idempotencyKey) {
        circuitBreaker.executeRunnable(() -> {
            try {
                BoundedRetry.call(() -> restClient.post()
                        .uri("/internal/drivers/{id}/release", driverId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("tripId", tripId, "reason", "saga compensation"))
                        .retrieve()
                        .toBodilessEntity());
            } catch (HttpClientErrorException clientError) {
                log.info("Driver {} no longer held for trip {}: {}", driverId, tripId, clientError.getStatusCode());
            }
        });
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
