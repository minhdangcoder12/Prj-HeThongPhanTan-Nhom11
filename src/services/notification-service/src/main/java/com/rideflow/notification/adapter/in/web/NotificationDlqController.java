package com.rideflow.notification.adapter.in.web;

import com.rideflow.notification.adapter.out.messaging.RabbitDlqAdapter;
import com.rideflow.notification.application.port.out.DlqPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications/dlq")
class NotificationDlqController {

    private final DlqPort dlqPort;

    NotificationDlqController(DlqPort dlqPort) {
        this.dlqPort = dlqPort;
    }

    @GetMapping
    Map<String, Object> status() {
        return Map.of(
                "queue", RabbitDlqAdapter.DLQ_QUEUE,
                "count", dlqPort.getDlqCount()
        );
    }

    @PostMapping("/reprocess")
    Map<String, Object> reprocess() {
        int reprocessed = dlqPort.reprocess();
        return Map.of(
                "reprocessed", reprocessed,
                "remaining", dlqPort.getDlqCount()
        );
    }
}

