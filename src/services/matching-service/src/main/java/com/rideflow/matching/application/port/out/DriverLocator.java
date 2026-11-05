package com.rideflow.matching.application.port.out;

import java.util.List;
import java.util.UUID;

public interface DriverLocator {
    List<UUID> nearestAvailable(double lat, double lng, int limit, List<UUID> excluded);
}

