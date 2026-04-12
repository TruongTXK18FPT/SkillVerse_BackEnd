package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry;
import java.util.List;

/**
 * Course catalog service for Phase 2 multi-level course-module matching.
 *
 * <p>This service maintains an in-memory index of PUBLIC courses and their modules,
 * refreshed periodically to avoid hitting the DB on every roadmap generation.
 *
 * <p>Used by the Pre-Selection algorithm (Phase A) to provide real, validated
 * course IDs before AI generates a roadmap — preventing hallucinated course references.
 *
 * @see AiCourseCatalogServiceImpl
 */
public interface AiCourseCatalogService {

    /**
     * Pre-select the best-matching courses for a given topic, scored and deduplicated.
     * This is Phase A of the Pre-Selection + Post-Matching algorithm.
     *
     * <p>Scoring formula:
     * <pre>
     * score = title_match*3 + category_match*2 + level_match*2 + description_match*1
     *       + enrollmentCount_normalized * 1
     * </pre>
     *
     * @param topic   The learning topic/goal to match against (e.g., "ReactJS", "Frontend Developer")
     * @param limit   Maximum number of courses to return (mode-specific: 5 for SKILL_BASED, 15 for CAREER_BASED)
     * @return List of scored course entries sorted by score descending
     */
    List<CourseCatalogEntry> preSelectCourses(String topic, int limit);

    /**
     * User-history-aware pre-selection variant.
     * Implementations should apply adaptive penalties for courses the user already
     * completed or is currently enrolled in, while keeping fallback behavior safe
     * when userId is null/unknown.
     */
    default List<CourseCatalogEntry> preSelectCourses(String topic, int limit, Long userId) {
        return preSelectCourses(topic, limit);
    }

    /**
     * Get all module IDs for a specific course, ordered by orderIndex.
     *
     * @param courseId The course ID to get modules for
     * @return List of module IDs in order, never null (empty list if no modules)
     */
    List<Long> getModuleIds(Long courseId);

    /**
     * Get module entries (id + title) for a specific course, ordered by orderIndex.
     *
     * @param courseId The course ID to get modules for
     * @return List of module entries, never null
     */
    List<CourseCatalogEntry.ModuleEntry> getModuleEntries(Long courseId);

    /**
     * Get prerequisite module IDs for a specific module within a course.
     *
     * @param courseId  The course ID
     * @param moduleId  The module ID
     * @return List of prerequisite module IDs (empty if none), never null
     */
    List<Long> getModulePrerequisiteIds(Long courseId, Long moduleId);

    /**
     * Force-refresh the in-memory course catalog from the database.
     * Called by the 5-minute scheduled refresh AND by the integration layer when needed.
     */
    void refresh();

    /**
     * Check whether the catalog has been loaded at least once.
     */
    boolean isLoaded();
}
