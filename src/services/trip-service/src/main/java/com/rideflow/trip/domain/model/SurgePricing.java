package com.rideflow.trip.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Hệ số giá = số chuyến đang chờ / số tài xế rảnh gần điểm đón, kẹp trong [1.0, 2.0]. */
public final class SurgePricing {

    public static final BigDecimal NONE = new BigDecimal("1.00");
    public static final BigDecimal MAX = new BigDecimal("2.00");

    private SurgePricing() {
    }

    public static BigDecimal multiplier(int waitingTrips, int availableDrivers) {
        if (waitingTrips <= 0) {
            return NONE;
        }
        if (availableDrivers <= 0) {
            return MAX;
        }
        return BigDecimal.valueOf(waitingTrips).divide(BigDecimal.valueOf(availableDrivers), 2, RoundingMode.HALF_UP)
                .max(NONE).min(MAX);
    }
}
