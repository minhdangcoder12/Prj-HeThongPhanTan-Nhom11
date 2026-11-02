package com.rideflow.notification.application.port.out;

import com.rideflow.notification.domain.model.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    List<Notification> findRecent(int limit);

    Optional<Notification> findById(UUID id);

    Notification save(Notification notification);
}
