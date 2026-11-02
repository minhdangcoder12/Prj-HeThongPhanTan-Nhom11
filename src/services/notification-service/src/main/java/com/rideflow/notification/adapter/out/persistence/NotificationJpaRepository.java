package com.rideflow.notification.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {
}
