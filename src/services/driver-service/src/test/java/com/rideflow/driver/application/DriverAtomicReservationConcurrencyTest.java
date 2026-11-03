package com.rideflow.driver.application;

import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.driver.application.port.out.DriverRepository;
import com.rideflow.driver.application.port.out.IdempotencyRepository;
import com.rideflow.driver.domain.model.Driver;
import com.rideflow.driver.domain.model.DriverStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverAtomicReservationConcurrencyTest {

    @Test
    void twentyConcurrentThreadsReserveSameDriver_exactlyOneWins() throws InterruptedException {
        UUID driverId = UUID.randomUUID();
        AtomicBoolean isReserved = new AtomicBoolean(false);

        Driver available = new Driver(driverId, UUID.randomUUID(), "Nguyen Van A", "29A-12345",
                DriverStatus.AVAILABLE, null, null, 10.7769, 106.7009, Instant.now());
        Driver reserved = new Driver(driverId, available.userId(), available.fullName(), available.vehiclePlate(),
                DriverStatus.RESERVED, UUID.randomUUID(), Instant.now().plusSeconds(30), 10.7769, 106.7009, Instant.now());

        DriverRepository repository = mock(DriverRepository.class);
        when(repository.findById(driverId)).thenAnswer(inv ->
                isReserved.get() ? Optional.of(reserved) : Optional.of(available)
        );

        // Atomic SQL update simulator (UPDATE drivers ... WHERE status='AVAILABLE'): exactly 1 compareAndSet succeeds
        when(repository.atomicReserve(eq(driverId), any(), any()))
                .thenAnswer(inv -> isReserved.compareAndSet(false, true));

        OutboxWriter outbox = mock(OutboxWriter.class);
        IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
        DriverCommands commands = new DriverCommands(repository, outbox, idempotency);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final UUID tripId = UUID.randomUUID();
            final String idemKey = "key-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    commands.reserveDriver(driverId, tripId, Duration.ofSeconds(30), idemKey);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // all 20 threads launch simultaneously
        endGate.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(19);
        verify(outbox, times(1)).append(any());
    }
}

