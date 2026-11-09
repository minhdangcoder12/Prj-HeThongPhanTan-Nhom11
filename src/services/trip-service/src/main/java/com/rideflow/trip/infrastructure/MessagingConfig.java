package com.rideflow.trip.infrastructure;

import com.rideflow.common.event.RideflowMessaging;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String NO_DRIVER_QUEUE = "trip.no-driver.q";
    public static final String PAYMENT_EVENTS_QUEUE = "trip.payment-events.q";

    @Bean
    Declarables tripNoDriverQueue() {
        return RideflowMessaging.consumerQueue(NO_DRIVER_QUEUE, "trip.no-driver");
    }

    @Bean
    Declarables tripPaymentEventsQueue() {
        return RideflowMessaging.consumerQueue(PAYMENT_EVENTS_QUEUE, "payment.succeeded", "payment.failed");
    }
}
