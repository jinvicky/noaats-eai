package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdapterConfigRepository extends JpaRepository<AdapterConfigEntity, String> {
    List<AdapterConfigEntity> findAllByOrderByNameAsc();
    List<AdapterConfigEntity> findByType(AdapterType type);
}
