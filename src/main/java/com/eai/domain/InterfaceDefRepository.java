package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterfaceDefRepository extends JpaRepository<InterfaceDef, String> {
    List<InterfaceDef> findAllByOrderByCreatedAtDesc();
    List<InterfaceDef> findByActiveTrue();
}
