package com.rideflow.user.adapter.out.security;

import com.rideflow.user.domain.model.Role;
import com.rideflow.user.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenIssuerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void tokenCarriesSubRoleDriverIdAnd8hExpiry() {
        UUID driverId = UUID.randomUUID();
        var user = new User(UUID.randomUUID(), "d@x.com", "D", Role.DRIVER, driverId, Instant.now());

        String token = new JwtTokenIssuer(SECRET).issue(user);

        Claims c = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertThat(c.getSubject()).isEqualTo(user.id().toString());
        assertThat(c.get("role")).isEqualTo("DRIVER");
        assertThat(c.get("driverId")).isEqualTo(driverId.toString());
        assertThat(c.getExpiration().toInstant())
                .isBetween(Instant.now().plusSeconds(8 * 3600 - 60), Instant.now().plusSeconds(8 * 3600 + 60));
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtTokenIssuer("too-short")).isInstanceOf(IllegalStateException.class);
    }
}
