package com.rideflow.notification.adapter.out.messaging;

import com.rideflow.notification.application.port.out.DlqPort;
import com.rideflow.notification.infrastructure.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class RabbitDlqAdapter implements DlqPort {

    public static final String DLQ_QUEUE = MessagingConfig.TRIP_EVENTS_QUEUE + ".dlq";
    private static final Logger log = LoggerFactory.getLogger(RabbitDlqAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;

    public RabbitDlqAdapter(RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
    }

    public static final String QUEUE_MESSAGE_COUNT_KEY = "QUEUE_MESSAGE_COUNT";

    @Override
    public int getDlqCount() {
        Properties props = amqpAdmin.getQueueProperties(DLQ_QUEUE);
        if (props != null) {
            Object count = props.get(QUEUE_MESSAGE_COUNT_KEY);
            if (count instanceof Number num) {
                return num.intValue();
            }
        }
        return 0;
    }

    @Override
    public int reprocess() {
        int count = 0;
        while (true) {
            Message message = rabbitTemplate.receive(DLQ_QUEUE, 1000);
            if (message == null) {
                break;
            }
            // Đẩy lại message từ DLQ về queue chính để xử lý lại
            rabbitTemplate.send("", MessagingConfig.TRIP_EVENTS_QUEUE, message);
            count++;
        }
        log.info("DLQ Reprocess: moved {} messages from {} to {}", count, DLQ_QUEUE, MessagingConfig.TRIP_EVENTS_QUEUE);
        return count;
    }
}
