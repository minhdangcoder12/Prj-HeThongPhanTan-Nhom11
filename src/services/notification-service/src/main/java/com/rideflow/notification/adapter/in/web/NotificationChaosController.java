package com.rideflow.notification.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Chaos endpoint cho Flow C; chỉ tồn tại trong profile demo (R8). */
@Profile("demo")
@RestController
@RequestMapping("/api/chaos/notification")
class NotificationChaosController {

    private static final Logger log = LoggerFactory.getLogger(NotificationChaosController.class);

    record FailRateRequest(
            @NotNull
            @DecimalMin("0.0")
            @DecimalMax("1.0")
            Double rate) {
    }

    private final NotificationChaosSwitch chaosSwitch;

    NotificationChaosController(NotificationChaosSwitch chaosSwitch) {
        this.chaosSwitch = chaosSwitch;
    }

    @PutMapping("/fail-rate")
    Map<String, Object> setFailRate(@Valid @RequestBody FailRateRequest req) {
        chaosSwitch.setFailRate(req.rate());
        log.warn("CHAOS: Notification fail rate set to {}", req.rate());
        return Map.of("rate", req.rate());
    }

    @GetMapping("/fail-rate")
    Map<String, Object> getFailRate() {
        return Map.of("rate", chaosSwitch.getFailRate());
    }
}
