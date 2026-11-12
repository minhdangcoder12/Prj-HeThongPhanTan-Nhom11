package com.rideflow.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(SECRET);
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = ex -> {
        forwarded.set(ex);
        return Mono.empty();
    };

    private static String token(SecretKey key, long expiresInMs) {
        return Jwts.builder().subject("u-1").claim("role", "DRIVER").claim("driverId", "d-1")
                .expiration(new Date(System.currentTimeMillis() + expiresInMs)).signWith(key).compact();
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        var exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void validTokenForwardsIdentityHeadersAndDropsSpoofedOnes() {
        run(MockServerHttpRequest.post("/api/trips")
                .header("Authorization", "Bearer " + token(KEY, 60_000))
                .header("X-User-Id", "spoofed").header("X-User-Role", "ADMIN").header("X-Driver-Id", "spoofed"));

        var h = forwarded.get().getRequest().getHeaders();
        assertThat(h.getFirst("X-User-Id")).isEqualTo("u-1");
        assertThat(h.getFirst("X-User-Role")).isEqualTo("DRIVER");
        assertThat(h.getFirst("X-Driver-Id")).isEqualTo("d-1");
    }

    @Test
    void spoofedHeadersAreStrippedOnPublicRequests() {
        run(MockServerHttpRequest.get("/api/trips").header("X-User-Id", "spoofed").header("X-User-Role", "DRIVER"));

        var h = forwarded.get().getRequest().getHeaders();
        assertThat(h.containsKey("X-User-Id")).isFalse();
        assertThat(h.containsKey("X-User-Role")).isFalse();
    }

    @Test
    void authAndMonitorPathsNeedNoToken() {
        run(MockServerHttpRequest.post("/api/auth/login"));
        assertThat(forwarded.getAndSet(null)).isNotNull();
        run(MockServerHttpRequest.post("/api/monitor/replay/x"));
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void writeWithoutTokenIs401ProblemJson() {
        var exchange = run(MockServerHttpRequest.post("/api/trips"));

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void expiredTokenIs401() {
        var exchange = run(MockServerHttpRequest.post("/api/trips")
                .header("Authorization", "Bearer " + token(KEY, -60_000)));

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongSignatureIs401() {
        var other = Keys.hmacShaKeyFor("ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8));
        var exchange = run(MockServerHttpRequest.post("/api/trips").header("Authorization", "Bearer " + token(other, 60_000)));

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtAuthGlobalFilter("short")).isInstanceOf(IllegalStateException.class);
    }
}
