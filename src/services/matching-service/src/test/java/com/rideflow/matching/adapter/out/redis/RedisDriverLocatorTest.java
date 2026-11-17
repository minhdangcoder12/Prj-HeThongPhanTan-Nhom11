package com.rideflow.matching.adapter.out.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDriverLocatorTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final GeoOperations<String, String> geoOps = mock(GeoOperations.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);

    private RedisDriverLocator locator;

    @BeforeEach
    void setUp() {
        when(redis.opsForGeo()).thenReturn(geoOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        locator = new RedisDriverLocator(redis, 20.0);
    }

    @Test
    void shouldAddDriverWithCorrectCoordinates() {
        UUID driverId = UUID.randomUUID();
        double lat = 10.7765;
        double lng = 106.7010;

        locator.addOrUpdateDriver(driverId, lat, lng);

        verify(geoOps).add(eq(RedisDriverLocator.DRIVERS_GEO_KEY), eq(new Point(lng, lat)), eq(driverId.toString()));
    }

    @Test
    void shouldRemoveDriverFromAvailablePool() {
        UUID driverId = UUID.randomUUID();

        locator.removeDriver(driverId);

        verify(zsetOps).remove(eq(RedisDriverLocator.DRIVERS_GEO_KEY), eq(driverId.toString()));
    }

    @Test
    void shouldFindNearestAvailableDriversAndFilterExcluded() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        UUID d3 = UUID.randomUUID();

        GeoResult<GeoLocation<String>> r1 = new GeoResult<>(new GeoLocation<>(d1.toString(), new Point(106.7, 10.7)), new Distance(1.0, Metrics.KILOMETERS));
        GeoResult<GeoLocation<String>> r2 = new GeoResult<>(new GeoLocation<>(d2.toString(), new Point(106.8, 10.8)), new Distance(2.5, Metrics.KILOMETERS));
        GeoResult<GeoLocation<String>> r3 = new GeoResult<>(new GeoLocation<>(d3.toString(), new Point(106.9, 10.9)), new Distance(4.0, Metrics.KILOMETERS));

        GeoResults<GeoLocation<String>> geoResults = new GeoResults<>(List.of(r1, r2, r3));

        when(geoOps.search(eq(RedisDriverLocator.DRIVERS_GEO_KEY), any(), any(Distance.class), any(GeoSearchCommandArgs.class)))
                .thenReturn(geoResults);

        // Exclude d2, limit 2
        List<UUID> nearest = locator.nearestAvailable(10.7, 106.7, 2, List.of(d2));

        assertThat(nearest).containsExactly(d1, d3);
    }

    @Test
    void shouldCountAvailableDriversInRadius() {
        GeoResult<GeoLocation<String>> r1 = new GeoResult<>(new GeoLocation<>("driver-1", new Point(106.7, 10.7)), new Distance(1.0, Metrics.KILOMETERS));
        GeoResult<GeoLocation<String>> r2 = new GeoResult<>(new GeoLocation<>("driver-2", new Point(106.8, 10.8)), new Distance(2.0, Metrics.KILOMETERS));

        GeoResults<GeoLocation<String>> geoResults = new GeoResults<>(List.of(r1, r2));

        when(geoOps.search(eq(RedisDriverLocator.DRIVERS_GEO_KEY), any(), any(Distance.class), any(GeoSearchCommandArgs.class)))
                .thenReturn(geoResults);

        int count = locator.countAvailable(10.7, 106.7, 3.0);

        assertThat(count).isEqualTo(2);
    }
}

