package com.rideflow.common.outbox;

import com.rideflow.common.event.RideflowMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Polls PENDING outbox rows and publishes them. Safe with several instances (SKIP LOCKED). */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String PENDING_SQL = "SELECT id, event_type, payload::text AS payload FROM outbox_event"
            + " WHERE status = 'PENDING' ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED";

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;

    public OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.tx = tx;
    }

    // shortcut: no publisher confirms, a broker-side drop after send() goes unnoticed; enable confirms if that matters.
    @Scheduled(fixedDelayString = "${rideflow.outbox.poll-ms:500}")
    public void publishPending() {
        tx.executeWithoutResult(status -> {
            for (Map<String, Object> row : jdbc.queryForList(PENDING_SQL)) {
                UUID id = (UUID) row.get("id");
                String type = (String) row.get("event_type");
                var message = MessageBuilder.withBody(((String) row.get("payload")).getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(id.toString())
                        .build();
                rabbit.send(RideflowMessaging.EVENTS_EXCHANGE, type, message);
                jdbc.update("UPDATE outbox_event SET status = 'PUBLISHED', published_at = now() WHERE id = ?", id);
                log.info("Published {} {}", type, id);
            }
        });
    }
}
