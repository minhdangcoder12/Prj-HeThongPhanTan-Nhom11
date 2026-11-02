package com.rideflow.notification.infrastructure;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String TRIP_EVENTS_QUEUE = "notification.trip-events.q";

    @Bean
    Declarables notificationQueues() {
        return RideflowMessaging.consumerQueue(TRIP_EVENTS_QUEUE, "trip.*", "payment.*");
    }
}
