package com.rideflow.payment.adapter.out.persistence;

import com.rideflow.payment.application.port.out.PaymentRepository;
import com.rideflow.payment.domain.model.Payment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    PaymentPersistenceAdapter(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Payment> findRecent(int limit) {
        return jpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(PaymentJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByTripId(UUID tripId) {
        return jpa.findByTripId(tripId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        return jpa.save(PaymentJpaEntity.fromDomain(payment)).toDomain();
    }
}
