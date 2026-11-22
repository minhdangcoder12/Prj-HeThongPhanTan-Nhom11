package com.rideflow.notification.adapter.out.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rideflow.notification.application.port.out.DlqPort;
import com.rideflow.notification.infrastructure.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
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

    /**
     * Chuyển toàn bộ message từ DLQ về queue chính an toàn tuyệt đối:
     * Tắt auto-ack (basicGet với autoAck = false). Chỉ gửi basicAck xóa khỏi DLQ
     * SAU KHI publish thành công vào queue chính.
     * Nếu xảy ra lỗi giữa chừng, gọi basicNack(requeue=true) để đẩy message trở lại DLQ,
     * ngăn chặn hoàn toàn nguy cơ mất message (zero message loss).
     */
    @Override
    public int reprocess() {
        Integer reprocessed = rabbitTemplate.execute(channel -> {
            int count = 0;
            while (true) {
                GetResponse response = channel.basicGet(DLQ_QUEUE, false);
                if (response == null) {
                    break;
                }
                long deliveryTag = response.getEnvelope().getDeliveryTag();
                try {
                    channel.basicPublish("", MessagingConfig.TRIP_EVENTS_QUEUE, response.getProps(), response.getBody());
                    channel.basicAck(deliveryTag, false);
                    count++;
                } catch (Exception e) {
                    channel.basicNack(deliveryTag, false, true);
                    log.error("Failed to reprocess message deliveryTag={} from DLQ, requeued safely", deliveryTag, e);
                    throw e;
                }
            }
            return count;
        });
        int count = reprocessed != null ? reprocessed : 0;
        log.info("DLQ Reprocess: safely moved {} messages from {} to {}", count, DLQ_QUEUE, MessagingConfig.TRIP_EVENTS_QUEUE);
        return count;
    }
}
