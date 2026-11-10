package com.rideflow.payment.adapter.out.gateway;

import com.rideflow.payment.application.port.out.PaymentGatewayPort;
import com.rideflow.payment.domain.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SimulatedPaymentGateway implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final double failureRate;

    public SimulatedPaymentGateway(@Value("${rideflow.payment.failure-rate:0.0}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public PaymentStatus process(UUID paymentId, UUID tripId, BigDecimal amount) {
        if (failureRate > 0.0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            log.warn("Simulated payment failure (rate={}) for payment {}", failureRate, paymentId);
            return PaymentStatus.FAILED;
        }
        log.info("Simulated payment success for payment {} (amount={})", paymentId, amount);
        return PaymentStatus.SUCCEEDED;
    }
}

