package com.rideflow.trip.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.infrastructure.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class NoDriverListener {

    private final TripCommands commands;

    NoDriverListener(TripCommands commands) {
        this.commands = commands;
    }

    @RabbitListener(queues = MessagingConfig.NO_DRIVER_QUEUE)
    void on(EventEnvelope<Map<String, Object>> event) {
        commands.markNoDriver(event);
    }
}
