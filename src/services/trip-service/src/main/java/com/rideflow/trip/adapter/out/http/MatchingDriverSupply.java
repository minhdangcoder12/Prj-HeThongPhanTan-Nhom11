package com.rideflow.trip.adapter.out.http;

import com.rideflow.trip.application.port.out.DriverSupply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
class MatchingDriverSupply implements DriverSupply {

    private record Supply(int availableDrivers) {}

    private final RestClient http;

    MatchingDriverSupply(@Value("${rideflow.services.matching:http://matching-service:8084}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory(); // timeout theo R4
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    @Override
    public int availableDrivers(double lat, double lng, double radiusKm) {
        Supply supply = http.get().uri("/internal/matching/supply?lat={lat}&lng={lng}&radiusKm={r}", lat, lng, radiusKm)
                .retrieve().body(Supply.class);
        if (supply == null) {
            throw new IllegalStateException("Empty supply response");
        }
        return supply.availableDrivers();
    }
}
