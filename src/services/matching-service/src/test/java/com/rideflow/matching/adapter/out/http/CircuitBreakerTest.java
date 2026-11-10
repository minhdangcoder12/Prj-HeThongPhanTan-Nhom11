package com.rideflow.matching.adapter.out.http;

import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.matching.application.MatchingSagaCoordinator;
import com.rideflow.matching.application.port.out.DriverLocator;
import com.rideflow.matching.application.port.out.MatchingSagaRepository;
import com.rideflow.matching.domain.model.MatchingSaga;
import com.rideflow.matching.domain.model.SagaStatus;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CircuitBreakerTest {

    private HttpServer server;
    private int port;
    private AtomicInteger requestCounter;
    private CircuitBreakerRegistry registry;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() throws IOException {
        requestCounter = new AtomicInteger(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(200))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        registry = CircuitBreakerRegistry.of(config);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("FT5: driver-service down -> circuit opens -> fast fail -> server recovers -> half_open -> closed")
    void driverServiceDown_tripsCircuitBreaker_andRecovers() throws Exception {
        // Return 500 Server Error to simulate service failure
        server.createContext("/internal/drivers/", exchange -> {
            requestCounter.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        HttpDriverServiceClient client = new HttpDriverServiceClient(
                "http://localhost:" + port, registry);
        CircuitBreaker cb = client.getCircuitBreaker();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // First 3 calls fail with 500
        for (int i = 0; i < 3; i++) {
            boolean reserved = client.reserve(UUID.randomUUID(), UUID.randomUUID(), "key-" + i);
            assertThat(reserved).isFalse();
        }

        // Circuit breaker should trip to OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsSoFar = requestCounter.get();

        // 4th call should fail FAST without hitting the backend server
        long startTime = System.currentTimeMillis();
        boolean fastFailReserved = client.reserve(UUID.randomUUID(), UUID.randomUUID(), "key-fast-fail");
        long durationMs = System.currentTimeMillis() - startTime;

        assertThat(fastFailReserved).isFalse();
        assertThat(durationMs).isLessThan(2000L); // fast fail in < 2s
        assertThat(requestCounter.get()).isEqualTo(callsSoFar); // no HTTP request made!

        // Wait for open state duration (200ms) to allow transition to HALF_OPEN
        Thread.sleep(250);

        // Reconfigure server to return 200 OK (server recovered)
        server.removeContext("/internal/drivers/");
        server.createContext("/internal/drivers/", exchange -> {
            requestCounter.incrementAndGet();
            byte[] response = "{\"status\":\"RESERVED\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        // In HALF_OPEN, first success
        boolean halfOpenCall1 = client.reserve(UUID.randomUUID(), UUID.randomUUID(), "key-recovered-1");
        assertThat(halfOpenCall1).isTrue();

        // Second success in HALF_OPEN -> transitions back to CLOSED
        boolean halfOpenCall2 = client.reserve(UUID.randomUUID(), UUID.randomUUID(), "key-recovered-2");
        assertThat(halfOpenCall2).isTrue();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("FT5: trip-service 500 error -> circuit opens -> fast fail assign")
    void tripServiceDown_tripsCircuitBreaker() {
        server.createContext("/internal/trips/", exchange -> {
            requestCounter.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        HttpTripServiceClient client = new HttpTripServiceClient(
                "http://localhost:" + port, registry);
        CircuitBreaker cb = client.getCircuitBreaker();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 3 consecutive 503 failures
        for (int i = 0; i < 3; i++) {
            boolean assigned = client.assignDriver(UUID.randomUUID(), UUID.randomUUID(), "key-" + i);
            assertThat(assigned).isFalse();
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsSoFar = requestCounter.get();

        // Fast fail check without making HTTP request
        long startTime = System.currentTimeMillis();
        boolean fastFail = client.assignDriver(UUID.randomUUID(), UUID.randomUUID(), "key-open");
        long durationMs = System.currentTimeMillis() - startTime;

        assertThat(fastFail).isFalse();
        assertThat(durationMs).isLessThan(500L);
        assertThat(requestCounter.get()).isEqualTo(callsSoFar);
    }

    @Test
    @DisplayName("FT5: when circuit breaker is OPEN, matching saga fails fast (<2s) and marks saga FAILED")
    void sagaFailsFastWhenCircuitBreakerIsOpen() {
        HttpDriverServiceClient driverClient = new HttpDriverServiceClient(
                "http://localhost:" + port, registry);
        HttpTripServiceClient tripClient = new HttpTripServiceClient(
                "http://localhost:" + port, registry);

        // Force driver circuit breaker to OPEN state
        driverClient.getCircuitBreaker().transitionToOpenState();
        assertThat(driverClient.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        MatchingSagaRepository repository = mock(MatchingSagaRepository.class);
        DriverLocator driverLocator = mock(DriverLocator.class);
        OutboxWriter outbox = mock(OutboxWriter.class);

        UUID tripId = UUID.randomUUID();
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();

        when(repository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(repository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driverLocator.nearestAvailable(anyDouble(), anyDouble(), anyInt(), anyList()))
                .thenReturn(List.of(driver1, driver2));

        MatchingSagaCoordinator coordinator = new MatchingSagaCoordinator(
                repository, driverLocator, driverClient, tripClient, outbox);

        long start = System.currentTimeMillis();
        MatchingSaga saga = coordinator.startOrResumeSaga(tripId, 10.762622, 106.660172, UUID.randomUUID());
        long duration = System.currentTimeMillis() - start;

        assertThat(duration).isLessThan(2000L);
        assertThat(saga.status()).isEqualTo(SagaStatus.FAILED);
    }
}
