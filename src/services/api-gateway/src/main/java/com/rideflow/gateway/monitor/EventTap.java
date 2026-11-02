package com.rideflow.gateway.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Copies every message on rideflow.events and rideflow.dlx into a ring buffer and a live stream.
 * Queue is non-durable/auto-delete: a gateway restart only loses monitor history, never business messages.
 */
@Configuration
public class EventTap {

    static final String EVENTS_EXCHANGE = "rideflow.events";
    static final String DEAD_LETTER_EXCHANGE = "rideflow.dlx";
    static final String TAP_QUEUE = "monitor.tap.q";
    private static final int CAPACITY = 200;

    public record TappedEvent(Instant receivedAt, String exchange, String routingKey, boolean deadLettered,
                              JsonNode body) {

        Optional<String> eventId() {
            return Optional.ofNullable(body.get("eventId")).map(JsonNode::asText);
        }
    }

    private final ObjectMapper objectMapper;
    private final Deque<TappedEvent> buffer = new ArrayDeque<>(CAPACITY);
    private final Sinks.Many<TappedEvent> live = Sinks.many().multicast().directBestEffort();

    EventTap(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    Declarables monitorTapTopology() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);
        Queue tap = new Queue(TAP_QUEUE, false, false, true);
        Binding all = BindingBuilder.bind(tap).to(events).with("#");
        Binding dead = BindingBuilder.bind(tap).to(dlx).with("#");
        return new Declarables(events, dlx, tap, all, dead);
    }

    @RabbitListener(queues = TAP_QUEUE)
    void onMessage(Message message) {
        var props = message.getMessageProperties();
        var event = new TappedEvent(Instant.now(), props.getReceivedExchange(), props.getReceivedRoutingKey(),
                DEAD_LETTER_EXCHANGE.equals(props.getReceivedExchange()), parse(message.getBody()));
        synchronized (buffer) {
            if (buffer.size() == CAPACITY) {
                buffer.removeFirst();
            }
            buffer.addLast(event);
        }
        live.tryEmitNext(event);
    }

    /** History first, then live events. */
    public Flux<TappedEvent> stream() {
        return Flux.concat(Flux.fromIterable(snapshot()), live.asFlux());
    }

    public List<TappedEvent> snapshot() {
        synchronized (buffer) {
            return List.copyOf(buffer);
        }
    }

    public Optional<TappedEvent> find(String eventId) {
        return snapshot().stream()
                .filter(e -> !e.deadLettered() && e.eventId().filter(eventId::equals).isPresent())
                .findFirst();
    }

    private JsonNode parse(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException notJson) {
            return TextNode.valueOf(new String(body));
        }
    }
}
