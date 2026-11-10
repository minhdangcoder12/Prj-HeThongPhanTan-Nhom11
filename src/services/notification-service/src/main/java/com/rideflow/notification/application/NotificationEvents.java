package com.rideflow.notification.application;

import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.notification.application.port.out.NotificationChaosPort;
import com.rideflow.notification.application.port.out.NotificationRepository;
import com.rideflow.notification.domain.model.Notification;
import com.rideflow.notification.domain.model.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationEvents {

    public static final String CONSUMER = "notification.trip-events";
    public static final String PRODUCER = "notification-service";
    private static final Logger log = LoggerFactory.getLogger(NotificationEvents.class);

    private final InboxGuard inbox;
    private final NotificationRepository repository;
    private final OutboxWriter outbox;
    private final NotificationChaosPort chaosPort;

    public NotificationEvents(InboxGuard inbox, NotificationRepository repository,
                              OutboxWriter outbox, Optional<NotificationChaosPort> chaosPort) {
        this.inbox = inbox;
        this.repository = repository;
        this.outbox = outbox;
        this.chaosPort = chaosPort.orElse(null);
    }

    /** Xử lý event từ RabbitMQ: kiểm tra chaos fail-rate, inbox chống trùng, gửi cho passenger và driver. */
    @Transactional
    public void handle(EventEnvelope<Map<String, Object>> event) {
        // C2.1: Nếu bật chaos fail-rate và trúng tỉ lệ lỗi -> ném exception để Spring AMQP retry rồi đưa vào DLQ
        if (chaosPort != null && chaosPort.shouldFail()) {
            log.warn("CHAOS: Simulating notification delivery failure for event {} {}", event.eventType(), event.eventId());
            throw new IllegalStateException("Simulated notification failure for event " + event.eventType() + " (" + event.eventId() + ")");
        }

        if (!inbox.firstTime(event.eventId(), CONSUMER, event.eventType())) {
            log.info("Duplicate {} {} ignored", event.eventType(), event.eventId());
            return;
        }

        UUID tripId = tripIdOf(event);
        UUID passengerId = extractUuid(event.payload(), "passengerId");
        UUID driverId = extractUuid(event.payload(), "driverId");

        boolean notified = false;

        if (passengerId != null) {
            String msg = buildMessage(event.eventType(), "passenger");
            recordAndPublish(event, tripId, passengerId, msg);
            notified = true;
        }

        if (driverId != null) {
            String msg = buildMessage(event.eventType(), "driver");
            recordAndPublish(event, tripId, driverId, msg);
            notified = true;
        }

        if (!notified) {
            String msg = "Event " + event.eventType() + " for aggregate " + event.aggregateId();
            recordAndPublish(event, tripId, null, msg);
        }

        log.info("Notification recorded for {} {}", event.eventType(), event.eventId());
    }

    private void recordAndPublish(EventEnvelope<?> event, UUID tripId, UUID recipientId, String message) {
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification(
                notificationId,
                tripId,
                recipientId,
                event.eventType(),
                message,
                NotificationStatus.SENT,
                1,
                Instant.now()
        );
        repository.save(notification);

        NotificationEventPayload payload = new NotificationEventPayload(
                notificationId,
                tripId,
                recipientId,
                event.eventType(),
                NotificationStatus.SENT
        );
        EventEnvelope<NotificationEventPayload> next = event.next("notification.sent", notificationId.toString(), PRODUCER, payload);
        outbox.append(next);
    }

    private String buildMessage(String eventType, String target) {
        return switch (eventType) {
            case "trip.created" -> "passenger".equals(target)
                    ? "Chuyến đi của bạn đã được tạo, hệ thống đang tìm tài xế..."
                    : "Có chuyến đi mới gần bạn.";
            case "trip.driver-assigned" -> "passenger".equals(target)
                    ? "Tài xế đã được phân công cho chuyến đi của bạn."
                    : "Bạn đã được phân công một chuyến đi mới.";
            case "trip.accepted" -> "passenger".equals(target)
                    ? "Tài xế đã nhận chuyến và đang đến điểm đón."
                    : "Bạn đã nhận chuyến đi thành công.";
            case "trip.started" -> "passenger".equals(target)
                    ? "Chuyến đi đã bắt đầu. Chúc bạn có một hành trình an toàn!"
                    : "Chuyến đi đã bắt đầu.";
            case "trip.completed" -> "passenger".equals(target)
                    ? "Chuyến đi đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!"
                    : "Chuyến đi đã hoàn thành thành công.";
            case "trip.driver-rejected" -> "passenger".equals(target)
                    ? "Tài xế đã từ chối chuyến đi, đang tiếp tục tìm tài xế khác..."
                    : "Bạn đã từ chối chuyến đi.";
            case "trip.no-driver" -> "passenger".equals(target)
                    ? "Rất tiếc, hiện tại không tìm thấy tài xế nào khả dụng quanh khu vực của bạn."
                    : "Không tìm thấy tài xế cho chuyến đi.";
            case "payment.succeeded" -> "passenger".equals(target)
                    ? "Thanh toán chuyến đi thành công!"
                    : "Chuyến đi đã được thanh toán thành công.";
            case "payment.failed" -> "passenger".equals(target)
                    ? "Thanh toán thất bại, vui lòng kiểm tra lại phương thức thanh toán."
                    : "Thanh toán chuyến đi thất bại.";
            default -> "Thông báo: " + eventType;
        };
    }

    private static UUID tripIdOf(EventEnvelope<?> event) {
        try {
            return UUID.fromString(event.aggregateId());
        } catch (IllegalArgumentException | NullPointerException notATripId) {
            return null;
        }
    }

    private static UUID extractUuid(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(payload.get(key)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
