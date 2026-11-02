package com.rideflow.common.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void nextKeepsCorrelationAndPointsCausationToParent() {
        var created = EventEnvelope.of("trip.created", "T1", "trip-service", null, Map.of("k", "v"));
        var assigned = created.next("trip.driver-assigned", "T1", "trip-service", Map.of());

        assertThat(assigned.correlationId()).isEqualTo(created.correlationId());
        assertThat(assigned.causationId()).isEqualTo(created.eventId());
        assertThat(assigned.eventId()).isNotEqualTo(created.eventId());
    }

    @Test
    void roundTripsThroughJsonWithSameEventId() throws Exception {
        var event = EventEnvelope.of("trip.created", "T1", "trip-service", null, Map.of("fare", 42));

        String json = mapper.writeValueAsString(event);
        EventEnvelope<Map<String, Object>> back = mapper.readValue(json, new TypeReference<>() { });

        assertThat(json).contains("\"occurredAt\":\"");
        assertThat(back.eventId()).isEqualTo(event.eventId());
        assertThat(back.payload()).containsEntry("fare", 42);
    }
}
