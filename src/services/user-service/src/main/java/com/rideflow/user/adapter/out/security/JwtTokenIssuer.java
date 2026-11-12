package com.rideflow.user.adapter.out.security;

import com.rideflow.user.application.port.out.TokenIssuer;
import com.rideflow.user.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
class JwtTokenIssuer implements TokenIssuer {

    private static final Duration TTL = Duration.ofHours(8);

    private final SecretKey key;

    /** Thiếu JWT_SECRET thì placeholder không resolve được và app không lên; ngắn hơn 32 byte cũng không lên. */
    JwtTokenIssuer(@Value("${rideflow.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public String issue(User user) {
        var builder = Jwts.builder()
                .subject(user.id().toString())
                .claim("role", user.role().name())
                .expiration(new Date(System.currentTimeMillis() + TTL.toMillis()));
        if (user.driverId() != null) {
            builder.claim("driverId", user.driverId().toString());
        }
        return builder.signWith(key).compact();
    }
}
