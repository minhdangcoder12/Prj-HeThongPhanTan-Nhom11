package com.rideflow.payment.adapter.out.persistence;

import com.rideflow.payment.domain.model.Payment;
import com.rideflow.payment.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "trip_id")
    private UUID tripId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private Integer attempts;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PaymentJpaEntity() {
    }

    static PaymentJpaEntity fromDomain(Payment payment) {
        var e = new PaymentJpaEntity();
        e.id = payment.id();
        e.tripId = payment.tripId();
        e.amount = payment.amount();
        e.status = payment.status();
        e.attempts = payment.attempts();
        e.createdAt = payment.createdAt();
        e.updatedAt = payment.updatedAt();
        return e;
    }

    Payment toDomain() {
        return new Payment(id, tripId, amount, status, attempts, createdAt, updatedAt);
    }
}
