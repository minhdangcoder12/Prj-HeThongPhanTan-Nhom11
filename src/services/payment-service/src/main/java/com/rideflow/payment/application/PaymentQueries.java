package com.rideflow.payment.application;

import com.rideflow.payment.application.port.out.PaymentRepository;
import com.rideflow.payment.domain.model.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read use cases; also feed the System Monitor. */
@Service
@Transactional(readOnly = true)
public class PaymentQueries {

    private static final int MONITOR_LIMIT = 100;

    private final PaymentRepository repository;

    public PaymentQueries(PaymentRepository repository) {
        this.repository = repository;
    }

    public List<Payment> recent() {
        return repository.findRecent(MONITOR_LIMIT);
    }

    public Optional<Payment> byId(UUID id) {
        return repository.findById(id);
    }
}
