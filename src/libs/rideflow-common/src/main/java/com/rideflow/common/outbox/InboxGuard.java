package com.rideflow.common.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Idempotent consumer: call inside the business transaction; a rollback also un-marks the event. */
public class InboxGuard {

    private final JdbcTemplate jdbc;

    public InboxGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true the first time this consumer sees eventId, false for a duplicate delivery. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId, String consumer, String eventType) {
        return jdbc.update("INSERT INTO inbox_event (event_id, consumer, event_type) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                eventId, consumer, eventType) == 1;
    }
}
