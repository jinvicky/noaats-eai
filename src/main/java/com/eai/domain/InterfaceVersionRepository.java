package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InterfaceVersionRepository extends JpaRepository<InterfaceVersion, String> {
    List<InterfaceVersion> findByInterfaceIdOrderByVersionNoDesc(String interfaceId);
    Optional<InterfaceVersion> findTopByInterfaceIdOrderByVersionNoDesc(String interfaceId);
}
