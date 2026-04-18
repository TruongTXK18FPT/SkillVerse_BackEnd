package com.exe.skillverse_backend.course_service.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.exe.skillverse_backend.shared.entity.Media;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "course_revisions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_course_revisions_course_revision", columnNames = {"course_id", "revision_number"})
        },
        indexes = {
                @Index(name = "idx_course_revisions_course_id", columnList = "course_id"),
                @Index(name = "idx_course_revisions_course_status", columnList = "course_id,status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseRevisionStatus status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String level;

    @Column(length = 120)
    private String category;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "estimated_duration_hours")
    private Integer estimatedDurationHours;

    @Column(length = 40)
    private String language;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 10)
    private String currency;

    @Column(name = "learning_objectives_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode learningObjectivesJson;

    @Column(name = "requirements_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode requirementsJson;

    @Column(name = "course_skill_tags_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode courseSkillTagsJson;

    /** Thumbnail media — each revision can carry its own thumbnail. Synced to Course.thumbnail on approval. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_media_id")
    private Media thumbnail;

    @Column(name = "content_snapshot_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode contentSnapshotJson;

    @Column(name = "source_revision_id")
    private Long sourceRevisionId;

    @Column(name = "baseline_snapshot_hash", length = 64)
    private String baselineSnapshotHash;

    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash;

    @Column(name = "rejected_snapshot_hash", length = 64)
    private String rejectedSnapshotHash;

    @Column(name = "snapshot_version")
    private Integer snapshotVersion;

    @Column(name = "source_course_status", nullable = false, length = 20)
    private String sourceCourseStatus;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
