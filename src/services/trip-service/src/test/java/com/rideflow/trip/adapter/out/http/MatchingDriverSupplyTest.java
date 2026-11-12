package com.rideflow.trip.adapter.out.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingDriverSupplyTest {

    @Test
    void readsAvailableDriversFromMatchingService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/matching/supply", ex -> {
            byte[] body = "{\"availableDrivers\":4,\"radiusKm\":3.0}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            var supply = new MatchingDriverSupply("http://127.0.0.1:" + server.getAddress().getPort());

            assertThat(supply.availableDrivers(10.77, 106.70, 3)).isEqualTo(4);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableMatchingServiceThrows() {
        var supply = new MatchingDriverSupply("http://127.0.0.1:1");

        assertThatThrownBy(() -> supply.availableDrivers(10.77, 106.70, 3)).isInstanceOf(RuntimeException.class);
    }
}
