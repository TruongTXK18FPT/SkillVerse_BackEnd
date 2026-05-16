package com.exe.skillverse_backend.runtime_settings.repository;

import com.exe.skillverse_backend.runtime_settings.entity.AppRuntimeSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppRuntimeSettingRepository extends JpaRepository<AppRuntimeSetting, String> {
}
