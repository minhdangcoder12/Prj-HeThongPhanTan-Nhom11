package com.rideflow.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.common.event.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional Outbox: stores the event inside the current transaction; OutboxRelay publishes it. */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope<?> event) {
        try {
            jdbc.update("INSERT INTO outbox_event (id, aggregate_id, event_type, payload) VALUES (?, ?, ?, ?::jsonb)",
                    event.eventId(), event.aggregateId(), event.eventType(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event payload is not serializable: " + event.eventType(), e);
        }
    }
}
