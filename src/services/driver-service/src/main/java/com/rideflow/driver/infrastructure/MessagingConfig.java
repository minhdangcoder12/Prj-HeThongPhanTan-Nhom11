package com.rideflow.driver.infrastructure;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String TRIP_EVENTS_QUEUE = "driver.trip-events.q";

    @Bean
    Declarables driverTripEventsQueue() {
        return RideflowMessaging.consumerQueue(TRIP_EVENTS_QUEUE, "trip.accepted", "trip.completed");
    }
}
