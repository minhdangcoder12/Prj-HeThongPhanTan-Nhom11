package com.rideflow.notification.adapter.in.web;

import com.rideflow.notification.application.NotificationQueries;
import com.rideflow.notification.domain.model.Notification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationQueries queries;

    NotificationController(NotificationQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    List<Notification> recent() {
        return queries.recent();
    }

    @GetMapping("/{id}")
    Notification byId(@PathVariable UUID id) {
        return queries.byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification " + id + " not found"));
    }
}
