package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, String> {
    List<PlatformUser> findAllByOrderByUsernameAsc();
    Optional<PlatformUser> findByUsername(String username);
    long countByActiveTrue();
}
