package com.rideflow.driver.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverTest {

    private final UUID id = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.now();

    private Driver createDriver(DriverStatus status) {
        return new Driver(id, userId, "Nguyen Van A", "29A-12345", status, null, null, 10.7769, 106.7009, now);
    }

    @Test
    void offlineDriverGoesAvailable() {
        Driver driver = createDriver(DriverStatus.OFFLINE);
        Driver updated = driver.changeAvailability(true);

        assertThat(updated.status()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(now);
    }

    @Test
    void availableDriverGoesOffline() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        Driver updated = driver.changeAvailability(false);

        assertThat(updated.status()).isEqualTo(DriverStatus.OFFLINE);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(now);
    }

    @Test
    void alreadyAvailableDriverIsNoOp() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        Driver updated = driver.changeAvailability(true);

        assertThat(updated).isSameAs(driver);
    }

    @Test
    void alreadyOfflineDriverIsNoOp() {
        Driver driver = createDriver(DriverStatus.OFFLINE);
        Driver updated = driver.changeAvailability(false);

        assertThat(updated).isSameAs(driver);
    }

    @Test
    void reservedDriverCannotChangeAvailability() {
        Driver driver = createDriver(DriverStatus.RESERVED);

        assertThatThrownBy(() -> driver.changeAvailability(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVED");

        assertThatThrownBy(() -> driver.changeAvailability(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVED");
    }

    @Test
    void onTripDriverCannotChangeAvailability() {
        Driver driver = createDriver(DriverStatus.ON_TRIP);

        assertThatThrownBy(() -> driver.changeAvailability(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ON_TRIP");

        assertThatThrownBy(() -> driver.changeAvailability(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ON_TRIP");
    }

    @Test
    void locationIsUpdated() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        Driver updated = driver.updateLocation(10.7800, 106.7100);

        assertThat(updated.lat()).isEqualTo(10.7800);
        assertThat(updated.lng()).isEqualTo(106.7100);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(now);
    }
}
