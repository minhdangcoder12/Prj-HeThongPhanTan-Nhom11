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

    @Test
    void availableDriverCanBeReserved() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        UUID tripId = UUID.randomUUID();
        Instant until = Instant.now().plusSeconds(30);

        Driver reserved = driver.reserve(tripId, until);

        assertThat(reserved.status()).isEqualTo(DriverStatus.RESERVED);
        assertThat(reserved.currentTripId()).isEqualTo(tripId);
        assertThat(reserved.reservedUntil()).isEqualTo(until);
        assertThat(reserved.updatedAt()).isAfterOrEqualTo(now);
    }

    @Test
    void cannotReserveNonAvailableDriver() {
        UUID tripId = UUID.randomUUID();
        Instant until = Instant.now().plusSeconds(30);

        Driver offlineDriver = createDriver(DriverStatus.OFFLINE);
        assertThatThrownBy(() -> offlineDriver.reserve(tripId, until))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OFFLINE");

        Driver onTripDriver = createDriver(DriverStatus.ON_TRIP);
        assertThatThrownBy(() -> onTripDriver.reserve(tripId, until))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ON_TRIP");
    }

    @Test
    void reservedDriverCanBeReleased() {
        UUID tripId = UUID.randomUUID();
        Driver reserved = new Driver(id, userId, "Nguyen Van A", "29A-12345",
                DriverStatus.RESERVED, tripId, Instant.now().plusSeconds(30), 10.7769, 106.7009, now);

        Driver released = reserved.release();

        assertThat(released.status()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(released.currentTripId()).isNull();
        assertThat(released.reservedUntil()).isNull();
    }

    @Test
    void releaseOnAvailableDriverIsNoOp() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        Driver released = driver.release();

        assertThat(released).isSameAs(driver);
    }

    @Test
    void cannotReleaseOfflineOrOnTripDriver() {
        Driver offlineDriver = createDriver(DriverStatus.OFFLINE);
        assertThatThrownBy(offlineDriver::release)
                .isInstanceOf(IllegalStateException.class);

        Driver onTripDriver = createDriver(DriverStatus.ON_TRIP);
        assertThatThrownBy(onTripDriver::release)
                .isInstanceOf(IllegalStateException.class);
    }
}
