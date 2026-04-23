package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByRunIdOrderBySeqAsc(String runId);
}
