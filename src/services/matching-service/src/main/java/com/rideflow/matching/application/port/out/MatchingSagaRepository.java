package com.rideflow.matching.application.port.out;

import com.rideflow.matching.domain.model.MatchingAttempt;
import com.rideflow.matching.domain.model.MatchingSaga;
import com.rideflow.matching.domain.model.SagaStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingSagaRepository {

    List<MatchingSaga> findRecent(int limit);

    Optional<MatchingSaga> findById(UUID id);

    Optional<MatchingSaga> findByTripId(UUID tripId);

    List<MatchingSaga> findByStatus(SagaStatus status);

    MatchingSaga save(MatchingSaga matchingSaga);

    void recordAttempt(MatchingAttempt attempt);

    List<MatchingAttempt> findAttemptsBySagaId(UUID sagaId);
}
