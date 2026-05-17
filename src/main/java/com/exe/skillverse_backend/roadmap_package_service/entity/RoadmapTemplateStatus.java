package com.exe.skillverse_backend.roadmap_package_service.entity;

public enum RoadmapTemplateStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    /**
     * Legacy statuses from the mentor package review flow. Kept so existing
     * rows can still be read while new templates use DRAFT/PUBLISHED/ARCHIVED.
     */
    SUBMITTED,
    APPROVED,
    REJECTED
}
