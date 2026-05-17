package com.exe.skillverse_backend.journey_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

import java.util.List;

/**
 * Entity representing a user's guided learning journey.
 * Tracks the entire flow from assessment to roadmap to study plans.
 */
@Entity
@Table(name = "journeys", indexes = {
        @Index(columnList = "user_id"),
        @Index(columnList = "status"),
        @Index(columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Journey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who owns this journey
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Type of journey: CAREER (for job role) or SKILL (for custom skills)
     */
    @Column(name = "type", length = 20)
    private String type;

    /**
     * Domain/field (e.g., "IT", "DESIGN", "BUSINESS")
     */
    @Column(nullable = false, length = 255)
    private String domain;

    /**
     * Human-readable journey title.
     * Keep this field for backward compatibility with existing DB schema
     * where column `title` is NOT NULL.
     */
    @Builder.Default
    @Column(name = "title", nullable = false, length = 255)
    private String title = "Untitled Journey";

    /**
     * Sub-category within the domain (e.g., "WEB_DEV", "MOBILE_APP")
     */
    @Column(name = "sub_category", length = 100)
    private String subCategory;

    /**
     * Industry name matching ExpertPromptConfig.industry (e.g., "Software Development", "Marketing")
     */
    @Column(name = "industry", length = 255)
    private String industry;

    /**
     * Job role for career type (e.g., "FRONTEND", "BACKEND")
     */
    @Column(name = "job_role", length = 100)
    private String jobRole;

    /**
     * Goal for taking the assessment (e.g., "Explore level", "Internship preparation")
     */
    @Column(name = "goal", length = 100)
    private String goal;

    /**
     * [V3] Skill duy nhất được chọn cho journey này (e.g., "REACT", "JAVA_SPRING_BOOT").
     * Mỗi journey chỉ gắn với 1 skill, thay vì multi-skill cũ.
     */
    @Column(name = "skill_name", length = 100)
    private String skillName;

    /**
     * Current status of the journey
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JourneyStatus status = JourneyStatus.NOT_STARTED;

    /**
     * User's current skill level after assessment (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", length = 20)
    private SkillLevel currentLevel;

    /**
     * JSON storing the assessment form data
     */
    @Column(name = "assessment_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String assessmentData;

    /**
     * ID of the generated roadmap (from ai_service)
     */
    @Column(name = "roadmap_session_id")
    private Long roadmapSessionId;

    @Column(name = "roadmap_template_id")
    private Long roadmapTemplateId;

    @Column(name = "job_position_track_id")
    private Long jobPositionTrackId;

    @Column(name = "roadmap_booking_id")
    private Long roadmapBookingId;

    @Column(name = "target_level", length = 50)
    private String targetLevel;

    @Column(name = "focus_skill_ids_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String focusSkillIdsJson;

    /**
     * Overall progress percentage (0-100)
     */
    @Builder.Default
    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;

    /**
     * AI-generated summary report
     */
    @Column(name = "ai_summary_report", columnDefinition = "TEXT")
    private String aiSummaryReport;

    /**
     * Timestamp when journey started
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Timestamp when journey was completed
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Last activity timestamp
     */
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    // ─── V3 Phase 1: final verification gate flags ─────────────────────────────
    // Null/false for legacy journeys so the gate only enforces on new flows.
    @Builder.Default
    @Column(name = "final_verification_required")
    private Boolean finalVerificationRequired = false;

    @Builder.Default
    @Column(name = "node_locked_after_verify")
    private Boolean nodeLockedAfterVerify = false;

    @Builder.Default
    @Column(name = "journey_output_verification_required")
    private Boolean journeyOutputVerificationRequired = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        lastActivityAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Journey status enum
     */
    public enum JourneyStatus {
        NOT_STARTED,
        ASSESSMENT_PENDING,
        TEST_IN_PROGRESS,
        EVALUATION_PENDING,
        ROADMAP_GENERATED,
        STUDY_PLAN_IN_PROGRESS,
        ACTIVE,
        COMPLETED,
        PAUSED,
        CANCELLED,
        // V3 Phase 1 — node mentoring / final verification gate lifecycle
        COMPLETED_UNVERIFIED,
        AWAITING_VERIFICATION,
        COMPLETED_VERIFIED
    }

    /**
     * Skill level enum
     */
    public enum SkillLevel {
        BEGINNER,
        ELEMENTARY,
        INTERMEDIATE,
        ADVANCED,
        EXPERT
    }
}
