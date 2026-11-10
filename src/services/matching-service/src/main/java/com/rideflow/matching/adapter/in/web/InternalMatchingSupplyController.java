package com.rideflow.matching.adapter.in.web;

import com.rideflow.matching.application.port.out.DriverLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** API nội bộ cung cấp thông tin nguồn cung tài xế cho trip-service tính cước động (task A9 / C9). */
@RestController
@RequestMapping("/internal/matching/supply")
public class InternalMatchingSupplyController {

    private final DriverLocator driverLocator;

    public InternalMatchingSupplyController(DriverLocator driverLocator) {
        this.driverLocator = driverLocator;
    }

    @GetMapping
    public Map<String, Object> supply(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "3.0") double radiusKm) {
        int count = driverLocator.countAvailable(lat, lng, radiusKm);
        return Map.of(
                "availableDrivers", count,
                "count", count,
                "radiusKm", radiusKm
        );
    }
}

