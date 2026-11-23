package com.rideflow.driver.adapter.in.messaging;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.driver.application.DriverCommands;
import com.rideflow.driver.infrastructure.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Tài xế theo vòng đời chuyến: nhận chuyến -> ON_TRIP, xong chuyến -> AVAILABLE. */
@Component
class TripLifecycleListener {

    static final String CONSUMER = "driver.trip-events";
    private static final Logger log = LoggerFactory.getLogger(TripLifecycleListener.class);

    private final InboxGuard inbox;
    private final DriverCommands commands;

    TripLifecycleListener(InboxGuard inbox, DriverCommands commands) {
        this.inbox = inbox;
        this.commands = commands;
    }

    @RabbitListener(queues = MessagingConfig.TRIP_EVENTS_QUEUE)
    @Transactional
    public void on(EventEnvelope<Map<String, Object>> event) {
        if (!inbox.firstTime(event.eventId(), CONSUMER, event.eventType())) {
            log.info("Duplicate {} {} ignored", event.eventType(), event.eventId());
            return;
        }
        Object driverId = event.payload() == null ? null : event.payload().get("driverId");
        if (driverId == null) {
            log.warn("{} {} has no driverId, ignored", event.eventType(), event.eventId());
            return;
        }
        UUID tripId = UUID.fromString(event.aggregateId());
        UUID driver = UUID.fromString(driverId.toString());
        if ("trip.accepted".equals(event.eventType())) {
            commands.startTrip(driver, tripId);
        } else {
            commands.finishTrip(driver, tripId);
        }
    }
}
