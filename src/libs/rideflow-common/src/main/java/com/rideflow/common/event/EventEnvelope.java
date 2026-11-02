package com.rideflow.common.event;

import com.rideflow.common.web.CorrelationIdFilter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

/** Envelope every event on {@code rideflow.events} must use. Routing key = {@code eventType}. */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        String aggregateId,
        String producer,
        Instant occurredAt,
        T payload) {

    /** New v1 event; correlationId falls back to the current request's MDC, then a fresh UUID. */
    public static <T> EventEnvelope<T> of(String eventType, String aggregateId, String producer,
                                          UUID causationId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, currentCorrelationId(), causationId,
                aggregateId, producer, Instant.now(), payload);
    }

    /** Next event in the same flow: keeps correlationId, causation = the event being handled. */
    public <R> EventEnvelope<R> next(String eventType, String aggregateId, String producer, R payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, correlationId, eventId,
                aggregateId, producer, Instant.now(), payload);
    }

    private static UUID currentCorrelationId() {
        String fromMdc = MDC.get(CorrelationIdFilter.MDC_KEY);
        try {
            return fromMdc != null ? UUID.fromString(fromMdc) : UUID.randomUUID();
        } catch (IllegalArgumentException notUuid) {
            return UUID.randomUUID();
        }
    }
}
