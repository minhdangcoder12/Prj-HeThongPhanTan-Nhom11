package com.rideflow.matching.application.port.out;

import com.rideflow.matching.domain.model.MatchingSaga;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingSagaRepository {

    List<MatchingSaga> findRecent(int limit);

    Optional<MatchingSaga> findById(UUID id);

    MatchingSaga save(MatchingSaga matchingSaga);
}
