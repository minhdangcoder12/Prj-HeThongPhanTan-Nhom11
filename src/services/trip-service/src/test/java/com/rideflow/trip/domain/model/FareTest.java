package com.rideflow.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FareTest {

    @Test
    void haversineDistanceInDistrictOne() {
        // Bến Thành -> Quận 5, khoảng 4,7 km đường chim bay
        assertThat(Fare.distanceKm(10.7765, 106.7010, 10.7626, 106.6602)).isCloseTo(4.72, within(0.05));
    }

    @Test
    void samePointCostsBaseFare() {
        assertThat(Fare.estimate(10.7765, 106.7010, 10.7765, 106.7010)).isEqualByComparingTo(new BigDecimal("12000"));
    }

    @Test
    void fareIsBasePlusPerKmRoundedUpToThousand() {
        // 12000 + 4000 * 4.72 = 30 880 -> 31 000
        assertThat(Fare.estimate(10.7765, 106.7010, 10.7626, 106.6602)).isEqualByComparingTo(new BigDecimal("31000"));
    }
}
