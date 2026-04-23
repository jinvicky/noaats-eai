package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RegisteredSystemRepository extends JpaRepository<RegisteredSystem, String> {
    List<RegisteredSystem> findAllByOrderByCodeAsc();
    Optional<RegisteredSystem> findByCode(String code);
    long countByActiveTrue();
}
