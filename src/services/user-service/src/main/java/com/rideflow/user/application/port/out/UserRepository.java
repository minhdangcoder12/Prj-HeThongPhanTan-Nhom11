package com.rideflow.user.application.port.out;

import com.rideflow.user.domain.model.User;
import com.rideflow.user.domain.model.UserCredential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    List<User> findRecent(int limit);

    Optional<User> findById(UUID id);

    Optional<UserCredential> findCredentialByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user, String passwordHash);
}
