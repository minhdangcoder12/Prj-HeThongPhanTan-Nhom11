package com.rideflow.gateway.monitor;

import com.rideflow.gateway.RideflowServices;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    private final ReactiveDiscoveryClient discovery;

    MonitorController(EventTap tap, RideflowServices services, ReactiveDiscoveryClient discovery) {
        this.tap = tap;
        this.services = services;
        this.discovery = discovery;
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

    /** UP when Consul has at least one passing instance (query-passing), else DOWN; e.g. Flow C stops notification. */
    @GetMapping("/health")
    Mono<Map<String, String>> health() {
        return Flux.fromIterable(services.services().entrySet())
                .flatMap(s -> discovery.getInstances(s.getKey() + "-service").hasElements()
                        .timeout(Duration.ofSeconds(2))
                        .onErrorReturn(false)
                        .map(up -> Map.entry(s.getKey(), up ? "UP" : "DOWN")))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, TreeMap::new);
    }
}
