package com.rideflow.trip.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.trip.application.port.out.DriverSupply;
import com.rideflow.trip.application.port.out.IdempotencyRepository;
import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.SurgePricing;
import com.rideflow.trip.domain.model.Trip;
import com.rideflow.trip.domain.model.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Use case ghi. Trạng thái, lịch sử và event outbox commit trong CÙNG một transaction (R3). */
@Service
@Transactional
public class TripCommands {

    static final String CREATE = "trip.create";
    static final String ASSIGN = "trip.assign-driver";
    static final String NO_DRIVER_CONSUMER = "trip.no-driver";
    static final String PAYMENT_CONSUMER = "trip.payment-events";
    private static final Logger log = LoggerFactory.getLogger(TripCommands.class);
    private static final double SURGE_RADIUS_KM = 3;
    private static final String PRODUCER = "trip-service";
    private static final String PASSENGER = "PASSENGER";
    private static final String DRIVER = "DRIVER";
    private static final String SYSTEM = "SYSTEM";

    private final TripRepository trips;
    private final IdempotencyRepository idempotency;
    private final OutboxWriter outbox;
    private final InboxGuard inbox;
    private final DriverSupply supply;

    public TripCommands(TripRepository trips, IdempotencyRepository idempotency, OutboxWriter outbox, InboxGuard inbox,
                        DriverSupply supply) {
        this.trips = trips;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.inbox = inbox;
        this.supply = supply;
    }

    public Trip create(UUID passengerId, double pickupLat, double pickupLng, double dropoffLat, double dropoffLng,
                       String clientKey) {
        // Key do client tự đặt, nên chỉ xác định được request khi đi kèm người gửi.
        String idempotencyKey = clientKey == null ? null : passengerId + ":" + clientKey;
        if (idempotencyKey != null) {
            var cached = idempotency.find(idempotencyKey, CREATE);
            if (cached.isPresent()) {
                return load(UUID.fromString(cached.get().body().replace("\"", "")));
            }
        }
        Trip trip = trips.save(Trip.request(passengerId, pickupLat, pickupLng, dropoffLat, dropoffLng,
                surgeAt(pickupLat, pickupLng), Instant.now()));
        publish(trip, null, "trip.created", PASSENGER, null);
        if (idempotencyKey != null) {
            idempotency.save(idempotencyKey, CREATE, 201, "\"" + trip.id() + "\"");
        }
        return trip;
    }

    /** Giá không được làm hỏng việc đặt xe: không hỏi được supply thì không tăng giá. */
    private BigDecimal surgeAt(double lat, double lng) {
        int waiting = trips.countWaitingNear(lat, lng, SURGE_RADIUS_KM) + 1; // +1 là chuyến đang đặt
        try {
            return SurgePricing.multiplier(waiting, supply.availableDrivers(lat, lng, SURGE_RADIUS_KM));
        } catch (RuntimeException e) {
            log.warn("Driver supply unavailable, surge multiplier 1.0: {}", e.getMessage());
            return SurgePricing.NONE;
        }
    }

    public Trip accept(UUID id, UUID driverId) {
        return move(id, driverId, "trip.accepted", t -> t.transitionTo(TripStatus.ACCEPTED, Instant.now()));
    }

    public Trip start(UUID id, UUID driverId) {
        return move(id, driverId, "trip.started", t -> t.transitionTo(TripStatus.IN_PROGRESS, Instant.now()));
    }

    public Trip complete(UUID id, UUID driverId) {
        return move(id, driverId, "trip.completed", t -> t.transitionTo(TripStatus.COMPLETED, Instant.now()));
    }

    /** matching cần tài xế vừa từ chối và điểm đón để chọn người khác. */
    public Trip reject(UUID id, UUID driverId) {
        Trip before = loadForDriver(id, driverId);
        Trip after = trips.save(before.rejectByDriver(Instant.now()));
        publish(after, before.status(), "trip.driver-rejected", DRIVER, before.driverId());
        return after;
    }

    /**
     * Bước của saga, gọi lại an toàn: cùng key trả kết quả cũ; lệnh song song chờ khoá dòng rồi thấy chuyến
     * đã gán cho tài xế này (không phát event lần 2). Tài xế khác -> 409.
     */
    public Trip assignDriver(UUID tripId, UUID driverId, String clientKey) {
        String idempotencyKey = tripId + ":" + clientKey; // gắn với chuyến: key dùng lại ở chuyến khác không bị bỏ qua
        if (idempotency.find(idempotencyKey, ASSIGN).isPresent()) {
            return load(tripId);
        }
        Trip before = load(tripId);
        if (before.status() == TripStatus.DRIVER_ASSIGNED && driverId.equals(before.driverId())) {
            return before;
        }
        Trip after = trips.save(before.assignDriver(driverId, Instant.now()));
        publish(after, before.status(), "trip.driver-assigned", SYSTEM, driverId);
        idempotency.save(idempotencyKey, ASSIGN, 200, "\"" + tripId + "\"");
        return after;
    }

    /** trip.no-driver do matching phát; chỉ ghi nhận, phát lại sẽ quay vòng về chính queue này. */
    public void markNoDriver(EventEnvelope<Map<String, Object>> event) {
        applyEvent(event, NO_DRIVER_CONSUMER, UUID.fromString(event.aggregateId()), TripStatus.NO_DRIVER);
    }

    /** aggregateId là paymentId, nên tripId lấy từ payload. */
    public void applyPayment(EventEnvelope<Map<String, Object>> event) {
        TripStatus target = "payment.succeeded".equals(event.eventType()) ? TripStatus.PAID : TripStatus.PAYMENT_FAILED;
        applyEvent(event, PAYMENT_CONSUMER, UUID.fromString(String.valueOf(event.payload().get("tripId"))), target);
    }

    /** Đổi trạng thái theo event của service khác: inbox chống trùng, kiểm trạng thái (R3), không phát lại event. */
    private void applyEvent(EventEnvelope<?> event, String consumer, UUID tripId, TripStatus target) {
        if (!inbox.firstTime(event.eventId(), consumer, event.eventType())) {
            log.info("Duplicate {} {} ignored", event.eventType(), event.eventId());
            return;
        }
        Trip before = load(tripId);
        if (!before.status().canMoveTo(target)) {
            // Event trễ hoặc sai thứ tự: ném lỗi chỉ retry vô ích rồi vào DLQ, nên WARN và bỏ qua.
            log.warn("Ignoring {} for trip {} in status {}", event.eventType(), tripId, before.status());
            return;
        }
        trips.save(before.transitionTo(target, Instant.now()));
        trips.recordTransition(tripId, before.status(), target, SYSTEM, event.correlationId());
    }

    private Trip move(UUID id, UUID driverId, String eventType, UnaryOperator<Trip> change) {
        Trip before = loadForDriver(id, driverId);
        Trip after = trips.save(change.apply(before));
        publish(after, before.status(), eventType, DRIVER, after.driverId());
        return after;
    }

    /** Kiểm dưới khoá dòng: tài xế khác (hoặc chuyến chưa có tài xế) không được thao tác. */
    private Trip loadForDriver(UUID id, UUID driverId) {
        Trip trip = load(id);
        if (driverId == null || !driverId.equals(trip.driverId())) {
            throw new NotTripDriverException(id);
        }
        return trip;
    }

    private Trip load(UUID id) {
        return trips.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("Trip " + id + " not found"));
    }

    private void publish(Trip trip, TripStatus from, String eventType, String actor, UUID driverId) {
        var event = EventEnvelope.of(eventType, trip.id().toString(), PRODUCER, null, TripEventPayload.of(trip, driverId));
        outbox.append(event);
        trips.recordTransition(trip.id(), from, trip.status(), actor, event.correlationId());
    }
}
