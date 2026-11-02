package com.rideflow.notification.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.notification.application.NotificationEvents;
import com.rideflow.notification.infrastructure.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Reference consumer: thin listener, dedupe + logic live in the use case. Mirror this in other services. */
@Component
class TripEventListener {

    private final NotificationEvents events;

    TripEventListener(NotificationEvents events) {
        this.events = events;
    }

    @RabbitListener(queues = MessagingConfig.TRIP_EVENTS_QUEUE)
    void on(EventEnvelope<Map<String, Object>> event) {
        events.handle(event);
    }
}
