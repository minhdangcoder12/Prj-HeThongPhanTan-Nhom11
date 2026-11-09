package com.rideflow.trip.infrastructure;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String NO_DRIVER_QUEUE = "trip.no-driver.q";

    @Bean
    Declarables tripNoDriverQueue() {
        return RideflowMessaging.consumerQueue(NO_DRIVER_QUEUE, "trip.no-driver");
    }
}
