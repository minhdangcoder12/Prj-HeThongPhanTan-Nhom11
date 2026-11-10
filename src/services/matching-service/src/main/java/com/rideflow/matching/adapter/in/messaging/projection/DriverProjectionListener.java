package com.rideflow.matching.adapter.in.messaging.projection;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.matching.adapter.out.redis.RedisDriverLocator;
import com.rideflow.matching.infrastructure.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Lắng nghe các event từ driver-service để cập nhật Redis GEO projection. */
@Component
public class DriverProjectionListener {

    public static final String CONSUMER = "matching.driver-projection";
    private static final Logger log = LoggerFactory.getLogger(DriverProjectionListener.class);

    private final RedisDriverLocator driverLocator;
    private final InboxGuard inbox;

    public DriverProjectionListener(RedisDriverLocator driverLocator, InboxGuard inbox) {
        this.driverLocator = driverLocator;
        this.inbox = inbox;
    }

    @RabbitListener(queues = MessagingConfig.DRIVER_EVENTS_QUEUE)
    @Transactional
    public void on(EventEnvelope<Map<String, Object>> event) {
        if (!inbox.firstTime(event.eventId(), CONSUMER, event.eventType())) {
            log.info("Duplicate driver event {} {} ignored", event.eventType(), event.eventId());
            return;
        }

        UUID driverId = extractDriverId(event);
        if (driverId == null) {
            log.warn("Driver event without valid driverId: {}", event);
            return;
        }

        Map<String, Object> payload = event.payload();
        String status = payload != null && payload.get("status") != null
                ? String.valueOf(payload.get("status"))
                : null;

        if ("AVAILABLE".equalsIgnoreCase(status) || "driver.released".equalsIgnoreCase(event.eventType())) {
            Double lat = extractCoordinate(payload, "lat");
            Double lng = extractCoordinate(payload, "lng");
            if (lat != null && lng != null) {
                driverLocator.addOrUpdateDriver(driverId, lat, lng);
            } else {
                log.warn("Driver {} marked AVAILABLE without location", driverId);
            }
        } else {
            // OFFLINE, RESERVED, ON_TRIP, hoặc driver.reserved
            driverLocator.removeDriver(driverId);
        }
    }

    private UUID extractDriverId(EventEnvelope<Map<String, Object>> event) {
        Map<String, Object> payload = event.payload();
        if (payload != null && payload.get("driverId") != null) {
            try {
                return UUID.fromString(String.valueOf(payload.get("driverId")));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid driverId UUID: {}", payload.get("driverId"));
            }
        }
        try {
            return UUID.fromString(event.aggregateId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Double extractCoordinate(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object locObj = payload.get("location");
        if (locObj instanceof Map<?, ?> locMap) {
            Object val = locMap.get(key);
            if (val instanceof Number num) {
                return num.doubleValue();
            }
        }
        Object direct = payload.get(key);
        if (direct instanceof Number num) {
            return num.doubleValue();
        }
        return null;
    }
}

