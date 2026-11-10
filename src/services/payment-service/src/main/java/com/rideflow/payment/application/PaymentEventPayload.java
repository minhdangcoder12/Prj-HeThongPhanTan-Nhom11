package com.rideflow.payment.application;

import com.rideflow.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Payload của event payment.*, theo PaymentEnvelope trong contracts/asyncapi/asyncapi.yaml. */
public record PaymentEventPayload(
        UUID paymentId,
        UUID tripId,
        BigDecimal amount,
        PaymentStatus status) {
}

