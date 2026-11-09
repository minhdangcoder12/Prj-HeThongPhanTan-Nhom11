package com.rideflow.trip.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.infrastructure.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** payment.succeeded / payment.failed -> PAID / PAYMENT_FAILED; inbox + state check live in the use case. */
@Component
class PaymentEventListener {

    private final TripCommands commands;

    PaymentEventListener(TripCommands commands) {
        this.commands = commands;
    }

    @RabbitListener(queues = MessagingConfig.PAYMENT_EVENTS_QUEUE)
    void on(EventEnvelope<Map<String, Object>> event) {
        commands.applyPayment(event);
    }
}
