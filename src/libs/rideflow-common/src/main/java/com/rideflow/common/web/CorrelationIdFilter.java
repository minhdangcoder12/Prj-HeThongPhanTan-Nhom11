package com.rideflow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Puts X-Correlation-Id (set by the gateway, or generated) into MDC and echoes it back. */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Gắn vào RestClient gọi /internal/**: service kia nhận cùng correlationId, nên event nó phát cùng một luồng (R6). */
    public static ClientHttpRequestInterceptor forwardOnCalls() {
        return (request, body, execution) -> {
            String id = MDC.get(MDC_KEY);
            if (id != null) {
                request.getHeaders().set(HEADER, id);
            }
            return execution.execute(request, body);
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
