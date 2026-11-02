package com.rideflow.common.event;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

public final class RideflowMessaging {

    public static final String EVENTS_EXCHANGE = "rideflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "rideflow.dlx";

    private RideflowMessaging() {
    }

    /**
     * Durable consumer queue bound to rideflow.events, plus its "queue.dlq".
     * After the bounded retries in rideflow-common.yml the message is rejected and lands in the DLQ.
     */
    public static Declarables consumerQueue(String queue, String... routingKeys) {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);
        Queue main = QueueBuilder.durable(queue)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queue)
                .build();
        Queue dlq = QueueBuilder.durable(queue + ".dlq").build();

        List<Declarable> all = new ArrayList<>(List.of(main, dlq, BindingBuilder.bind(dlq).to(dlx).with(queue)));
        for (String key : routingKeys) {
            all.add(BindingBuilder.bind(main).to(events).with(key));
        }
        return new Declarables(all);
    }
}
