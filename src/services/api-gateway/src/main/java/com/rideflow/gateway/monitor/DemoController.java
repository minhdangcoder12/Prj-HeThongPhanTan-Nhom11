package com.rideflow.gateway.monitor;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Demo-only triggers (rule R8): replay a captured event (Flow D) and publish a test event. */
@RestController
@RequestMapping("/api/monitor")
@Profile("demo")
class DemoController {

    private final EventTap tap;
    private final RabbitTemplate rabbit;

    DemoController(EventTap tap, RabbitTemplate rabbit) {
        this.tap = tap;
        this.rabbit = rabbit;
    }

    /** Re-publishes the exact same message (same eventId): consumers must apply it only once. */
    @PostMapping("/replay/{eventId}")
    Mono<Map<String, String>> replay(@PathVariable String eventId) {
        var event = tap.find(eventId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event " + eventId + " not in monitor buffer"));
        byte[] body = event.body().toString().getBytes(StandardCharsets.UTF_8);
        return publish(event.routingKey(), body)
                .thenReturn(Map.of("replayed", eventId, "routingKey", event.routingKey()));
    }

    /** Smoke test for the event pipeline: shows up on the Monitor without any business service. */
    @PostMapping("/test-event")
    Mono<Map<String, String>> testEvent() {
        String id = UUID.randomUUID().toString();
        String json = """
                {"eventId":"%s","eventType":"system.ping","eventVersion":1,"correlationId":"%s","causationId":null,\
                "aggregateId":"gateway","producer":"api-gateway","occurredAt":"%s","payload":{}}"""
                .formatted(id, UUID.randomUUID(), Instant.now());
        return publish("system.ping", json.getBytes(StandardCharsets.UTF_8)).thenReturn(Map.of("eventId", id));
    }

    private Mono<Void> publish(String routingKey, byte[] body) {
        var message = MessageBuilder.withBody(body).setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        return Mono.fromRunnable(() -> rabbit.send(EventTap.EVENTS_EXCHANGE, routingKey, message))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }
}
