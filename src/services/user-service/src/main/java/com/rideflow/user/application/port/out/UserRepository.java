package com.rideflow.user.application.port.out;

import com.rideflow.user.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    List<User> findRecent(int limit);

    Optional<User> findById(UUID id);

    User save(User user);
}
