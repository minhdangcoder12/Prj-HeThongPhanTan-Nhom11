package com.rideflow.payment.application.port.out;

import com.rideflow.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {

    PaymentStatus process(UUID paymentId, UUID tripId, BigDecimal amount);
}

