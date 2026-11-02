package com.rideflow.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Every request entering the system gets an X-Correlation-Id (rule R6). */
@Component
class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, correlationId)))
                .build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
