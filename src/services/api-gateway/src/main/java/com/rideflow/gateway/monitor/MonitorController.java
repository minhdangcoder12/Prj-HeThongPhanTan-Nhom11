package com.rideflow.gateway.monitor;

import com.rideflow.gateway.RideflowServices;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/monitor")
class MonitorController {

    private final EventTap tap;
    private final RideflowServices services;
    private final WebClient http;

    MonitorController(EventTap tap, RideflowServices services, WebClient.Builder http) {
        this.tap = tap;
        this.services = services;
        this.http = http.build();
    }

    /** Live event feed for the System Monitor (history replayed first). */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<EventTap.TappedEvent>> events() {
        Flux<ServerSentEvent<EventTap.TappedEvent>> data = tap.stream().map(e -> ServerSentEvent.builder(e).build());
        // Comment heartbeats keep proxies (Vite dev server included) from closing an idle stream.
        Flux<ServerSentEvent<EventTap.TappedEvent>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<EventTap.TappedEvent>builder().comment("ping").build());
        return Flux.merge(data, heartbeat);
    }

    /** UP/DOWN of every service, so the demo shows what is running (e.g. Flow C stops notification). */
    @GetMapping("/health")
    Mono<Map<String, String>> health() {
        return Flux.fromIterable(services.services().entrySet())
                .flatMap(s -> http.get().uri(s.getValue() + "/actuator/health").retrieve().bodyToMono(Map.class)
                        .map(body -> String.valueOf(body.get("status")))
                        .timeout(Duration.ofSeconds(2))
                        .onErrorReturn("DOWN")
                        .map(status -> Map.entry(s.getKey(), status)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, TreeMap::new);
    }
}
