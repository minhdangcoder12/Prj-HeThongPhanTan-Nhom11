package com.rideflow.trip.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurgePricingTest {

    @Test
    void oneDriverAndThreeWaitingRaisesPrice() {
        assertThat(SurgePricing.multiplier(3, 1)).isGreaterThan(SurgePricing.NONE);
    }

    @Test
    void manyDriversKeepNormalPrice() {
        assertThat(SurgePricing.multiplier(1, 10)).isEqualByComparingTo("1.00");
    }

    @Test
    void multiplierIsClampedAtTwo() {
        assertThat(SurgePricing.multiplier(50, 2)).isEqualByComparingTo("2.00");
    }

    @Test
    void noDriversWithDemandIsMax() {
        assertThat(SurgePricing.multiplier(1, 0)).isEqualByComparingTo("2.00");
    }

    @Test
    void ratioIsRoundedToTwoDecimals() {
        assertThat(SurgePricing.multiplier(4, 3)).isEqualByComparingTo("1.33");
    }
}
