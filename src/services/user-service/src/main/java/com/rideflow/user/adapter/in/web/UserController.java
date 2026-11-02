package com.rideflow.user.adapter.in.web;

import com.rideflow.user.application.UserQueries;
import com.rideflow.user.domain.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserQueries queries;

    UserController(UserQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    List<User> recent() {
        return queries.recent();
    }

    @GetMapping("/{id}")
    User byId(@PathVariable UUID id) {
        return queries.byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + id + " not found"));
    }
}
