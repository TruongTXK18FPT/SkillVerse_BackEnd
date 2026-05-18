package com.exe.skillverse_backend.portfolio_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Stores skills that have been verified by a mentor through the
 * ROADMAP_MENTORING final verification (Jitsi meeting) flow.
 *
 * One record per (user_id, skill_name) — re-verification overwrites.
 */
@Entity
@Table(name = "user_verified_skills",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "skill_name"}),
        indexes = @Index(columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVerifiedSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Canonical skill name (UPPER_SNAKE), e.g. "REACT", "JAVA_SPRING_BOOT". */
    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "verified_by_mentor_id", nullable = false)
    private Long verifiedByMentorId;

    /** Journey that led to this verification. */
    @Column(name = "journey_id")
    private Long journeyId;

    /** The ROADMAP_MENTORING booking under which verification occurred. */
    @Column(name = "booking_id")
    private Long bookingId;

    /** Skill level at time of verification (e.g. BEGINNER, ADVANCED). */
    @Column(name = "skill_level", length = 20)
    private String skillLevel;

    /** Free-text note from mentor about the verification outcome. */
    @Column(name = "verification_note", columnDefinition = "TEXT")
    private String verificationNote;

    /**
     * Optional user-controlled ranking for skills that should appear first on
     * public mentor cards and portfolio badges. Null means normal chronological
     * ordering.
     */
    @Column(name = "featured_order")
    private Integer featuredOrder;

    @CreationTimestamp
    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;
}
