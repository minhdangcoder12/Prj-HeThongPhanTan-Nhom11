package com.rideflow.matching.adapter.out.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedRetryTest {

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void timeoutIsRetriedUntilItSucceeds() {
        String result = BoundedRetry.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("read timed out");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterThreeAttempts() {
        assertThatThrownBy(() -> BoundedRetry.call(() -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("connection refused");
        })).isInstanceOf(ResourceAccessException.class);

        assertThat(calls).hasValue(BoundedRetry.MAX_ATTEMPTS);
    }

    @Test
    void clientErrorIsARealAnswerAndIsNotRetried() {
        assertThatThrownBy(() -> BoundedRetry.call(() -> {
            calls.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.CONFLICT);
        })).isInstanceOf(HttpClientErrorException.class);

        assertThat(calls).hasValue(1);
    }
}
