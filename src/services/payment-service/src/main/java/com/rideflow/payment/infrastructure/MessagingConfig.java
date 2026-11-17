package com.rideflow.payment.infrastructure;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String TRIP_COMPLETED_QUEUE = "payment.trip-completed.q";

    @Bean
    Declarables paymentQueues() {
        return RideflowMessaging.consumerQueue(TRIP_COMPLETED_QUEUE, "trip.completed");
    }
}

