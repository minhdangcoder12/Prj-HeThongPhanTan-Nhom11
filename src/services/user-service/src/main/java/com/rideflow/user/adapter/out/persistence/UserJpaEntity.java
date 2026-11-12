package com.rideflow.user.adapter.out.persistence;

import com.rideflow.user.domain.model.Role;
import com.rideflow.user.domain.model.User;
import com.rideflow.user.domain.model.UserCredential;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    private UUID id;

    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected UserJpaEntity() {
    }

    static UserJpaEntity fromDomain(User user, String passwordHash) {
        var e = new UserJpaEntity();
        e.id = user.id();
        e.email = user.email();
        e.fullName = user.fullName();
        e.role = user.role();
        e.passwordHash = passwordHash;
        e.driverId = user.driverId();
        e.createdAt = user.createdAt();
        return e;
    }

    User toDomain() {
        return new User(id, email, fullName, role, driverId, createdAt);
    }

    UserCredential toCredential() {
        return new UserCredential(toDomain(), passwordHash);
    }
}
