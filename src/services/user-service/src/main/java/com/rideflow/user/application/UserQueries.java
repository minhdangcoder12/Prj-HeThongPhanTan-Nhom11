package com.rideflow.user.application;

import com.rideflow.user.application.port.out.UserRepository;
import com.rideflow.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read use cases; also feed the System Monitor. */
@Service
@Transactional(readOnly = true)
public class UserQueries {

    private static final int MONITOR_LIMIT = 100;

    private final UserRepository repository;

    public UserQueries(UserRepository repository) {
        this.repository = repository;
    }

    public List<User> recent() {
        return repository.findRecent(MONITOR_LIMIT);
    }

    public Optional<User> byId(UUID id) {
        return repository.findById(id);
    }
}
