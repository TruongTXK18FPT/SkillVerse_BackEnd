package com.exe.skillverse_backend.meowl_chat_service.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported role modes for role-aware Meowl guidance.
 */
public enum MeowlRoleMode {
    LEARNER,
    MENTOR,
    RECRUITER,
    GENERAL;

    public static Optional<MeowlRoleMode> fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LEARNER", "USER" -> Optional.of(LEARNER);
            case "MENTOR" -> Optional.of(MENTOR);
            case "RECRUITER", "BUSINESS" -> Optional.of(RECRUITER);
            case "GENERAL" -> Optional.of(GENERAL);
            default -> Optional.empty();
        };
    }
}
