package com.rideflow.trip.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.common.event.EventEnvelope;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.trip.application.TripCommands;
import com.rideflow.trip.application.port.out.DriverSupply;
import com.rideflow.trip.application.port.out.TripRepository;
import com.rideflow.trip.domain.model.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy use case trên Postgres thật (schema Flyway) để ./mvnw verify kiểm cả SQL viết tay
 * (lịch sử, idempotency, inbox, FOR UPDATE) và khoá ngoại trips <-> trip_status_history.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({TripPersistenceAdapter.class, IdempotencyJdbcRepository.class, TripCommands.class, TripPostgresTest.Messaging.class})
class TripPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Messaging {
        @Bean
        OutboxWriter outboxWriter(JdbcTemplate jdbc) {
            return new OutboxWriter(jdbc, new ObjectMapper().findAndRegisterModules());
        }

        /** 1 tài xế rảnh quanh mọi điểm đón. */
        @Bean
        DriverSupply driverSupply() {
            return (lat, lng, radiusKm) -> 1;
        }

        @Bean
        InboxGuard inboxGuard(JdbcTemplate jdbc) {
            return new InboxGuard(jdbc);
        }
    }

    @Autowired
    TripCommands commands;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TripRepository repository;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private Trip newTrip(UUID passenger, String key) {
        return commands.create(passenger, 10.7765, 106.7010, 10.7626, 106.6602, key);
    }

    @Test
    void createAndRetriedAssignWriteEachRowOnce() {
        UUID passenger = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        Trip trip = newTrip(passenger, "k1");

        assertThat(newTrip(passenger, "k1").id()).isEqualTo(trip.id());           // cùng hành khách + key -> cùng chuyến
        assertThat(newTrip(UUID.randomUUID(), "k1").id()).isNotEqualTo(trip.id()); // cùng key, hành khách khác -> chuyến riêng
        commands.assignDriver(trip.id(), driver, "saga-1:assign");
        commands.assignDriver(trip.id(), driver, "saga-1:assign");               // saga retry, cùng key
        commands.assignDriver(trip.id(), driver, "saga-1:assign-again");         // cùng tài xế, key mới

        assertThat(jdbc.queryForObject("select status from trips where id = ?", String.class, trip.id()))
                .isEqualTo("DRIVER_ASSIGNED");
        assertThat(count("select count(*) from trip_status_history where trip_id = ?", trip.id())).isEqualTo(2);
        assertThat(count("select count(*) from outbox_event where aggregate_id = ?", trip.id().toString())).isEqualTo(2);
        assertThat(count("select count(*) from idempotency_record where idempotency_key like ?", trip.id() + ":%"))
                .isEqualTo(1);
    }

    @Test
    void surgeIsStoredAndOnlyNearbyWaitingTripsCount() {
        // Điểm đón riêng (lat 20.0) để không lẫn với test khác dùng chung container.
        UUID passenger = UUID.randomUUID();
        Trip first = commands.create(passenger, 20.0, 106.0, 20.01, 106.01, null);
        commands.create(passenger, 20.01, 106.0, 20.02, 106.01, null);               // ~1,1 km
        Trip third = commands.create(passenger, 20.0, 106.02, 20.01, 106.01, null);  // ~2,1 km
        Trip far = commands.create(passenger, 20.0, 106.5, 20.01, 106.01, null);     // ~52 km, không tính

        assertThat(first.surgeMultiplier()).isEqualByComparingTo("1.00"); // 1 chờ / 1 tài xế
        assertThat(third.surgeMultiplier()).isEqualByComparingTo("2.00"); // 3 chờ / 1 tài xế, kẹp 2.0
        assertThat(far.surgeMultiplier()).isEqualByComparingTo("1.00");
        assertThat(jdbc.queryForObject("select surge_multiplier from trips where id = ?", java.math.BigDecimal.class,
                third.id())).isEqualByComparingTo("2.00");

        assertThat(repository.countWaitingNear(20.0, 106.0, 3)).isEqualTo(3);
        commands.assignDriver(first.id(), UUID.randomUUID(), "k"); // đã ghép xong thì không còn chờ
        assertThat(repository.countWaitingNear(20.0, 106.0, 3)).isEqualTo(2);
    }

    @Test
    void duplicatePaymentEventIsAppliedOnce() {
        Trip trip = newTrip(UUID.randomUUID(), null);
        commands.assignDriver(trip.id(), UUID.randomUUID(), "k");
        commands.accept(trip.id());
        commands.start(trip.id());
        commands.complete(trip.id());
        var paid = EventEnvelope.<Map<String, Object>>of("payment.succeeded", UUID.randomUUID().toString(),
                "payment-service", null, Map.of("tripId", trip.id().toString()));

        commands.applyPayment(paid);
        commands.applyPayment(paid); // giao lại: inbox đã có dòng này

        assertThat(jdbc.queryForObject("select status from trips where id = ?", String.class, trip.id())).isEqualTo("PAID");
        assertThat(count("select count(*) from trip_status_history where trip_id = ? and to_status = 'PAID'", trip.id()))
                .isEqualTo(1);
    }
}
