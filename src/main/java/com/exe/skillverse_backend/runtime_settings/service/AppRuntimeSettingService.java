package com.exe.skillverse_backend.runtime_settings.service;

import com.exe.skillverse_backend.runtime_settings.entity.AppRuntimeSetting;
import com.exe.skillverse_backend.runtime_settings.repository.AppRuntimeSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Runtime settings service for RAG provider toggles.
 *
 * Keys:
 *   ai.local_generation.runtime_enabled  — default true
 *   ai.rag_service.runtime_enabled       — default false
 *   ai.java_rag_fallback.runtime_enabled — default true
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppRuntimeSettingService {

    static final String KEY_AI_RAG_SERVICE = "ai.rag_service.runtime_enabled";
    static final String KEY_JAVA_RAG_FALLBACK = "ai.java_rag_fallback.runtime_enabled";
    static final String KEY_LOCAL_AI_GENERATION = "ai.local_generation.runtime_enabled";

    private final AppRuntimeSettingRepository repository;

    public boolean isAiRagServiceRuntimeEnabled() {
        return getBooleanSetting(KEY_AI_RAG_SERVICE, false);
    }

    public boolean isLocalAiGenerationRuntimeEnabled() {
        return getBooleanSetting(KEY_LOCAL_AI_GENERATION, true);
    }

    public boolean isJavaRagFallbackRuntimeEnabled() {
        return getBooleanSetting(KEY_JAVA_RAG_FALLBACK, true);
    }

    @Transactional
    public void setAiRagServiceEnabled(boolean enabled, Long updatedBy) {
        saveSetting(KEY_AI_RAG_SERVICE, String.valueOf(enabled), updatedBy);
        log.info("AI-RAG-Service runtime toggle set to {} by userId={}", enabled, updatedBy);
    }

    @Transactional
    public void setLocalAiGenerationEnabled(boolean enabled, Long updatedBy) {
        saveSetting(KEY_LOCAL_AI_GENERATION, String.valueOf(enabled), updatedBy);
        log.info("Local AI generation runtime toggle set to {} by userId={}", enabled, updatedBy);
    }

    @Transactional
    public void setJavaRagFallbackEnabled(boolean enabled, Long updatedBy) {
        saveSetting(KEY_JAVA_RAG_FALLBACK, String.valueOf(enabled), updatedBy);
        log.info("Java RAG Fallback runtime toggle set to {} by userId={}", enabled, updatedBy);
    }

    private boolean getBooleanSetting(String key, boolean defaultValue) {
        try {
            return repository.findById(key)
                    .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            log.warn("Failed to read runtime setting '{}', using default={}: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    private void saveSetting(String key, String value, Long updatedBy) {
        AppRuntimeSetting setting = repository.findById(key)
                .orElse(new AppRuntimeSetting(key, value));
        setting.setSettingValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        setting.setUpdatedBy(updatedBy);
        repository.save(setting);
    }
}
