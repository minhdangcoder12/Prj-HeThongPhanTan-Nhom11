package com.rideflow.payment.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.payment.application.PaymentCommands;
import com.rideflow.payment.infrastructure.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TripCompletedListener {

    private final PaymentCommands commands;

    public TripCompletedListener(PaymentCommands commands) {
        this.commands = commands;
    }

    @RabbitListener(queues = MessagingConfig.TRIP_COMPLETED_QUEUE)
    public void on(EventEnvelope<Map<String, Object>> event) {
        commands.handleTripCompleted(event);
    }
}

