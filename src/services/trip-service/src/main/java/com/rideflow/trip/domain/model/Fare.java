package com.rideflow.trip.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cước (VND) = giá mở cửa + giá mỗi km đường chim bay, làm tròn lên 1.000. */
public final class Fare {

    static final BigDecimal BASE = BigDecimal.valueOf(12_000);
    static final BigDecimal PER_KM = BigDecimal.valueOf(4_000);
    private static final BigDecimal ROUND_TO = BigDecimal.valueOf(1_000);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private Fare() {
    }

    public static BigDecimal estimate(double fromLat, double fromLng, double toLat, double toLng) {
        BigDecimal raw = BASE.add(PER_KM.multiply(BigDecimal.valueOf(distanceKm(fromLat, fromLng, toLat, toLng))));
        return raw.divide(ROUND_TO, 0, RoundingMode.CEILING).multiply(ROUND_TO);
    }

    /** Khoảng cách đường chim bay (công thức haversine). */
    public static double distanceKm(double fromLat, double fromLng, double toLat, double toLng) {
        double dLat = Math.toRadians(toLat - fromLat);
        double dLng = Math.toRadians(toLng - fromLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
