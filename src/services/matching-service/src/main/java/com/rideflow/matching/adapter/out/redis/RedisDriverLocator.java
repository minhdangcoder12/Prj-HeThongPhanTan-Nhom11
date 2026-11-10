package com.rideflow.matching.adapter.out.redis;

import com.rideflow.matching.application.port.out.DriverLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class RedisDriverLocator implements DriverLocator {

    public static final String DRIVERS_GEO_KEY = "drivers:available:geo";
    private static final Logger log = LoggerFactory.getLogger(RedisDriverLocator.class);

    private final StringRedisTemplate redis;
    private final double defaultSearchRadiusKm;

    public RedisDriverLocator(StringRedisTemplate redis,
                              @Value("${rideflow.matching.search-radius-km:20.0}") double defaultSearchRadiusKm) {
        this.redis = redis;
        this.defaultSearchRadiusKm = defaultSearchRadiusKm;
    }

    /** Cập nhật hoặc thêm tài xế khả dụng vào Redis GEO. Điểm trong Redis là (lng, lat). */
    public void addOrUpdateDriver(UUID driverId, double lat, double lng) {
        redis.opsForGeo().add(DRIVERS_GEO_KEY, new Point(lng, lat), driverId.toString());
        log.info("Redis GEO: driver {} added/updated at ({}, {})", driverId, lat, lng);
    }

    /** Xoá tài xế khỏi danh sách khả dụng (khi OFFLINE, RESERVED, ON_TRIP...). */
    public void removeDriver(UUID driverId) {
        redis.opsForZSet().remove(DRIVERS_GEO_KEY, driverId.toString());
        log.info("Redis GEO: driver {} removed from available pool", driverId);
    }

    @Override
    public List<UUID> nearestAvailable(double lat, double lng, int limit, List<UUID> excluded) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        int fetchLimit = limit + (excluded != null ? excluded.size() : 0) + 10;
        GeoSearchCommandArgs args = GeoSearchCommandArgs.newGeoSearchArgs()
                .sortAscending()
                .includeDistance()
                .limit(fetchLimit);

        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(
                DRIVERS_GEO_KEY,
                GeoReference.fromCoordinate(new Point(lng, lat)),
                new Distance(defaultSearchRadiusKm, Metrics.KILOMETERS),
                args
        );

        if (results == null || results.getContent().isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> nearest = new ArrayList<>();
        for (GeoResult<GeoLocation<String>> res : results) {
            try {
                UUID driverId = UUID.fromString(res.getContent().getName());
                if (excluded == null || !excluded.contains(driverId)) {
                    nearest.add(driverId);
                    if (nearest.size() >= limit) {
                        break;
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid driver UUID in Redis GEO: {}", res.getContent().getName());
            }
        }
        return nearest;
    }

    @Override
    public int countAvailable(double lat, double lng, double radiusKm) {
        GeoSearchCommandArgs args = GeoSearchCommandArgs.newGeoSearchArgs().sortAscending();
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(
                DRIVERS_GEO_KEY,
                GeoReference.fromCoordinate(new Point(lng, lat)),
                new Distance(radiusKm > 0 ? radiusKm : defaultSearchRadiusKm, Metrics.KILOMETERS),
                args
        );
        return results != null ? results.getContent().size() : 0;
    }
}

