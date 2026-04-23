package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, String> {
    List<ExecutionRun> findTop100ByOrderByStartedAtDesc();
    List<ExecutionRun> findByInterfaceIdOrderByStartedAtDesc(String interfaceId);
    List<ExecutionRun> findByStartedAtAfterOrderByStartedAtAsc(Instant after);
    long countByStatus(RunStatus status);
    long countByStartedAtAfter(Instant after);
    long countByStatusAndStartedAtAfter(RunStatus status, Instant after);
}
