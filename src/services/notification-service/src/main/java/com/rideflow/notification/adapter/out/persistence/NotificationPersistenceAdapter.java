package com.rideflow.notification.adapter.out.persistence;

import com.rideflow.notification.application.port.out.NotificationRepository;
import com.rideflow.notification.domain.model.Notification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class NotificationPersistenceAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    NotificationPersistenceAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Notification> findRecent(int limit) {
        return jpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(NotificationJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(NotificationJpaEntity::toDomain);
    }

    @Override
    public Notification save(Notification notification) {
        return jpa.save(NotificationJpaEntity.fromDomain(notification)).toDomain();
    }
}
