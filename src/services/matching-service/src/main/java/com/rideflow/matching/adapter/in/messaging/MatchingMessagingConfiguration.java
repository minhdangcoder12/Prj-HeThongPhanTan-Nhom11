package com.rideflow.matching.adapter.in.messaging;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchingMessagingConfiguration {

    public static final String QUEUE_TRIP_CREATED = "matching.trip-created";
    public static final String ROUTING_KEY_TRIP_CREATED = "trip.created";

    public static final String QUEUE_DRIVER_REJECTED = "matching.driver-rejected";
    public static final String ROUTING_KEY_DRIVER_REJECTED = "trip.driver-rejected";

    @Bean
    Declarables matchingTripCreatedQueues() {
        return RideflowMessaging.consumerQueue(QUEUE_TRIP_CREATED, ROUTING_KEY_TRIP_CREATED);
    }

    @Bean
    Declarables matchingDriverRejectedQueues() {
        return RideflowMessaging.consumerQueue(QUEUE_DRIVER_REJECTED, ROUTING_KEY_DRIVER_REJECTED);
    }
}
