package com.rideflow.notification.application;

import com.rideflow.notification.application.port.out.NotificationRepository;
import com.rideflow.notification.domain.model.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read use cases; also feed the System Monitor. */
@Service
@Transactional(readOnly = true)
public class NotificationQueries {

    private static final int MONITOR_LIMIT = 100;

    private final NotificationRepository repository;

    public NotificationQueries(NotificationRepository repository) {
        this.repository = repository;
    }

    public List<Notification> recent() {
        return repository.findRecent(MONITOR_LIMIT);
    }

    public Optional<Notification> byId(UUID id) {
        return repository.findById(id);
    }
}
