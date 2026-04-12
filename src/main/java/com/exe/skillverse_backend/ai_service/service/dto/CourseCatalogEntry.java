package com.exe.skillverse_backend.ai_service.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * In-memory course catalog entry used by the Pre-Selection algorithm (Phase A).
 *
 * <p>These entries are indexed in a ConcurrentHashMap and refreshed every 5 minutes.
 * Each entry holds the minimal data needed for scoring and matching — no heavy
 * entity data is loaded into memory.
 *
 * <p>Scoring weights (used by {@link com.exe.skillverse_backend.ai_service.service.AiCourseCatalogService}):
 * <ul>
 *   <li>title match       → ×3</li>
 *   <li>category match    → ×2</li>
 *   <li>level match       → ×2</li>
 *   <li>description match  → ×1</li>
 *   <li>enrollment count  → normalized ×1</li>
 * </ul>
 *
 * @see com.exe.skillverse_backend.ai_service.service.AiCourseCatalogService
 * @see com.exe.skillverse_backend.ai_service.service.impl.MultiLevelCourseMatcher
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseCatalogEntry {

    private Long id;
    private String title;
    private String description;
    private String shortDescription;
    private String category;
    private String level;
    private int moduleCount;
    private long enrollmentCount;

    /** Placeholder-safe average rating signal. Defaults to 0 when unavailable. */
    @Builder.Default
    private double averageRating = 0.0;

    private Instant createdAt;

    /**
     * Transient score computed during pre-selection ranking.
     * Not stored in the index — set on cloned entries after scoring.
     * Higher score = better match to the topic.
     */
    @Builder.Default
    private Integer score = 0;

    /** Module IDs ordered by orderIndex — used for module distribution */
    @Builder.Default
    private List<Long> moduleIds = List.of();

    /**
     * Individual module entry for detailed frontend display.
     * Contains only id + title — lightweight, no nested content.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModuleEntry {
        private Long id;
        private String title;
        private int orderIndex;
    }

    @Builder.Default
    private List<ModuleEntry> modules = List.of();
}
