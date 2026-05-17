package com.exe.skillverse_backend.journey_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to start a new journey with minimal data for AI assessment.
 * This is a simplified form for the Guided Journey feature.
 *
 * The goal is to collect minimal data to generate an AI assessment test.
 * Additional data (background, learning preferences, goals, challenges) will be
 * collected after the user completes the assessment test.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartJourneyRequest {

    // ==================== Type ====================

    /**
     * Type of journey: CAREER (for job role) or SKILL (for custom skills)
     */
    private String type; // "CAREER" or "SKILL"

    // ==================== Required Fields ====================

    /**
     * Target domain/field (e.g., "IT", "DESIGN", "BUSINESS")
     * This is the primary area the user wants to be assessed in
     */
    @NotBlank(message = "Domain is required")
    private String domain;

    /**
     * Goal/purpose for taking the assessment
     */
    @NotBlank(message = "Goal is required")
    private String goal;

    /**
     * Self-assessed experience level (BEGINNER, ELEMENTARY, INTERMEDIATE, ADVANCED, EXPERT)
     */
    @NotBlank(message = "Level is required")
    private String level;

    // ==================== Optional Fields ====================

    /**
     * Job role for career type (e.g., "FRONTEND", "BACKEND", "UI_DESIGNER")
     */
    private String jobRole;

    /**
     * Sub-category within the domain (e.g., "WEB_DEV", "MOBILE_APP" for IT domain)
     */
    private String subCategory;

    /**
     * Industry name matching ExpertPromptConfig.industry (e.g., "Software Development", "Marketing")
     * Sent from frontend after mapping subCategory → industry display name
     */
    private String industry;

    /**
     * Canonical taxonomy IDs for job-position based journey flow.
     * When jobPositionTrackId is present, backend derives the assessment skills
     * from JobPositionTrackSkill instead of trusting user-selected skills.
     */
    private Long jobPositionId;

    private Long jobPositionTrackId;

    /**
        * Target skills user wants to develop in this journey.
        * In V3 flow this is typically a focused list (often one primary skill).
     */
    private List<String> skills;

        /**
        * Skills user already has.
        * This list is used to calibrate prompt context so AI can avoid re-testing obvious basics.
        */
        private List<String> existingSkills;

    /**
     * Areas the user wants to focus on in the assessment
     * Options: FUNDAMENTALS, PROBLEM_SOLVING, PRACTICAL_CODING, JOB_READINESS, TECHNICAL_ENGLISH
     */
    private List<String> focusAreas;

    /**
     * Preferred language for the test
     * Options: VI (Vietnamese), EN (English), BILINGUAL
     */
    private String language;

    /**
     * Test duration preference
     * Options: QUICK (5 min), STANDARD (10-15 min), DEEP (20-30 min)
     */
    private String duration;

    /**
     * Desired number of assessment questions.
     * Frontend currently offers 10 / 15 / 25 and backend normalizes safely.
     */
    private Integer questionCount;
}
