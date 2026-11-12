package com.rideflow.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Kiểm JWT cho /api/** (R8). Luôn xoá X-User-* từ client rồi mới gắn lại từ claims, để không giả mạo được danh tính.
 * Public: /api/auth/**, /api/monitor/** và mọi GET (màn hình quan sát chỉ đọc).
 */
@Component
class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final SecretKey key;

    JwtAuthGlobalFilter(@Value("${rideflow.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getURI().getPath();
        Claims claims = parse(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

        boolean isPublic = request.getMethod() == HttpMethod.GET
                || path.startsWith("/api/auth/") || path.startsWith("/api/monitor/");
        if (claims == null && !isPublic) {
            return unauthorized(exchange);
        }
        return chain.filter(exchange.mutate().request(r -> r.headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Role");
            h.remove("X-Driver-Id");
            if (claims != null) {
                h.set("X-User-Id", claims.getSubject());
                h.set("X-User-Role", String.valueOf(claims.get("role")));
                Object driverId = claims.get("driverId");
                if (driverId != null) {
                    h.set("X-Driver-Id", driverId.toString());
                }
            }
        })).build());
    }

    private Claims parse(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(authorization.substring(7)).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null; // hết hạn, sai chữ ký hay rác đều coi như chưa đăng nhập
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = ("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"Missing or invalid bearer token\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /** Sau RequestSize (default filter, order nhỏ) để body quá lớn vẫn bị 413 dù chưa có token. */
    @Override
    public int getOrder() {
        return 10;
    }
}
