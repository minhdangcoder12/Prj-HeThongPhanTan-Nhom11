package com.rideflow.matching.infrastructure;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String DRIVER_EVENTS_QUEUE = "matching.driver-events.q";

    @Bean
    Declarables matchingDriverEventsQueue() {
        return RideflowMessaging.consumerQueue(
                DRIVER_EVENTS_QUEUE,
                "driver.availability-changed",
                "driver.location-updated",
                "driver.reserved",
                "driver.released"
        );
    }
}

