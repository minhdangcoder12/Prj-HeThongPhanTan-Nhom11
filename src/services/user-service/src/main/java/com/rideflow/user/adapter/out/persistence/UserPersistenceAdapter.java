package com.rideflow.user.adapter.out.persistence;

import com.rideflow.user.application.port.out.UserRepository;
import com.rideflow.user.domain.model.User;
import com.rideflow.user.domain.model.UserCredential;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    UserPersistenceAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<User> findRecent(int limit) {
        return jpa.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(UserJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<UserCredential> findCredentialByEmail(String email) {
        return jpa.findByEmail(email).map(UserJpaEntity::toCredential);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public User save(User user, String passwordHash) {
        return jpa.save(UserJpaEntity.fromDomain(user, passwordHash)).toDomain();
    }
}
