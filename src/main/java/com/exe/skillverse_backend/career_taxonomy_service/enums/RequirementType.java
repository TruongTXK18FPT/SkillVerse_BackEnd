package com.exe.skillverse_backend.career_taxonomy_service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RequirementType {
    REQUIRED,
    IMPORTANT,
    NICE_TO_HAVE,
    /**
     * Legacy value kept so old DB rows can still be loaded by JPA before data is migrated.
     * New requests and responses should use NICE_TO_HAVE instead.
     */
    @Deprecated
    OPTIONAL;

    @JsonCreator
    public static RequirementType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return REQUIRED;
        }
        return switch (value.trim().toUpperCase()) {
            case "OPTIONAL", "NICE_TO_HAVE" -> NICE_TO_HAVE;
            case "RECOMMENDED", "IMPORTANT" -> IMPORTANT;
            case "REQUIRED" -> REQUIRED;
            default -> REQUIRED;
        };
    }

    @JsonValue
    public String toJson() {
        return normalized().name();
    }

    public RequirementType normalized() {
        return this == OPTIONAL ? NICE_TO_HAVE : this;
    }
}
