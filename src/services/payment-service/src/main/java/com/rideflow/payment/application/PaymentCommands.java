package com.rideflow.payment.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.payment.application.port.out.PaymentGatewayPort;
import com.rideflow.payment.application.port.out.PaymentRepository;
import com.rideflow.payment.domain.model.Payment;
import com.rideflow.payment.domain.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentCommands {

    public static final String CONSUMER = "payment.trip-completed";
    public static final String PRODUCER = "payment-service";
    private static final Logger log = LoggerFactory.getLogger(PaymentCommands.class);

    private final InboxGuard inbox;
    private final OutboxWriter outbox;
    private final PaymentRepository repository;
    private final PaymentGatewayPort gateway;

    public PaymentCommands(InboxGuard inbox, OutboxWriter outbox,
                           PaymentRepository repository, PaymentGatewayPort gateway) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.repository = repository;
        this.gateway = gateway;
    }

    /** Xử lý event trip.completed: inbox chống trùng, giả lập thanh toán, lưu payment và phát payment.* qua outbox. */
    @Transactional
    public void handleTripCompleted(EventEnvelope<Map<String, Object>> event) {
        if (!inbox.firstTime(event.eventId(), CONSUMER, event.eventType())) {
            log.info("Duplicate {} {} ignored", event.eventType(), event.eventId());
            return;
        }

        UUID tripId = extractTripId(event);
        if (tripId == null) {
            log.warn("Received trip.completed without valid tripId: {}", event);
            return;
        }

        if (repository.findByTripId(tripId).isPresent()) {
            log.warn("Payment already exists for trip {}", tripId);
            return;
        }

        BigDecimal amount = extractFare(event);
        UUID paymentId = UUID.randomUUID();

        PaymentStatus status = gateway.process(paymentId, tripId, amount);
        Payment payment = Payment.create(paymentId, tripId, amount, status);
        repository.save(payment);

        String outboxEventType = status == PaymentStatus.SUCCEEDED ? "payment.succeeded" : "payment.failed";
        PaymentEventPayload payload = new PaymentEventPayload(paymentId, tripId, amount, status);
        EventEnvelope<PaymentEventPayload> next = event.next(outboxEventType, paymentId.toString(), PRODUCER, payload);
        outbox.append(next);

        log.info("Payment {} processed for trip {}: status={}, amount={}", paymentId, tripId, status, amount);
    }

    private UUID extractTripId(EventEnvelope<Map<String, Object>> event) {
        Map<String, Object> payload = event.payload();
        if (payload != null && payload.get("tripId") != null) {
            try {
                return UUID.fromString(String.valueOf(payload.get("tripId")));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tripId UUID: {}", payload.get("tripId"));
            }
        }
        try {
            return UUID.fromString(event.aggregateId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private BigDecimal extractFare(EventEnvelope<Map<String, Object>> event) {
        Map<String, Object> payload = event.payload();
        if (payload != null && payload.get("fare") != null) {
            try {
                return new BigDecimal(String.valueOf(payload.get("fare")));
            } catch (NumberFormatException e) {
                log.warn("Invalid fare number: {}", payload.get("fare"));
            }
        }
        return BigDecimal.valueOf(30000);
    }
}

