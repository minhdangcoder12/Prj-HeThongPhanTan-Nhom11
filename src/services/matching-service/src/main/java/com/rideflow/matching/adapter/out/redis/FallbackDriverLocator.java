package com.rideflow.matching.adapter.out.redis;

import com.rideflow.matching.application.port.out.DriverLocator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnMissingBean(type = "com.rideflow.matching.adapter.out.redis.RedisDriverLocator")
public class FallbackDriverLocator implements DriverLocator {

    private static final List<UUID> DEMO_DRIVERS = List.of(
            UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
            UUID.fromString("00000000-0000-0000-0000-0000000000d2"),
            UUID.fromString("00000000-0000-0000-0000-0000000000d3")
    );

    @Override
    public List<UUID> nearestAvailable(double lat, double lng, int limit, List<UUID> excluded) {
        return DEMO_DRIVERS.stream()
                .filter(id -> excluded == null || !excluded.contains(id))
                .limit(limit)
                .toList();
    }
}

