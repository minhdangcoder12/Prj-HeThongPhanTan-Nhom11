package com.rideflow.payment.application.port.out;

import com.rideflow.payment.domain.model.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    List<Payment> findRecent(int limit);

    Optional<Payment> findById(UUID id);

    Payment save(Payment payment);
}
