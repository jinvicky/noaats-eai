package com.eai.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, String> {

    default PlatformSettings loadOrDefault() {
        return findById(PlatformSettings.SINGLETON_ID).orElseGet(PlatformSettings::new);
    }
}
