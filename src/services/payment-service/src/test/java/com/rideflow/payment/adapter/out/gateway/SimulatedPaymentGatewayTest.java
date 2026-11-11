package com.rideflow.payment.adapter.out.gateway;

import com.rideflow.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    @Test
    void shouldAlwaysSucceedWhenFailureRateIsZero() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(0.0);
        for (int i = 0; i < 20; i++) {
            PaymentStatus status = gateway.process(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(50000));
            assertThat(status).isEqualTo(PaymentStatus.SUCCEEDED);
        }
    }

    @Test
    void shouldAlwaysFailWhenFailureRateIsOne() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(1.0);
        for (int i = 0; i < 20; i++) {
            PaymentStatus status = gateway.process(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(50000));
            assertThat(status).isEqualTo(PaymentStatus.FAILED);
        }
    }
}

