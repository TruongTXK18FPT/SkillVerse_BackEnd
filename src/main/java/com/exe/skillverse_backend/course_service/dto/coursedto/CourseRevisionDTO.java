package com.exe.skillverse_backend.course_service.dto.coursedto;

import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseRevisionDTO {
    private Long id;
    private Long courseId;
    private Integer revisionNumber;
    private CourseRevisionStatus status;

    private String title;
    private String description;
    private String level;
    private String category;
    private String shortDescription;
    private Integer estimatedDurationHours;
    private String language;
    private BigDecimal price;
    private String currency;

    private String learningObjectivesJson;
    private String requirementsJson;
    private String courseSkillTagsJson;
    private String contentSnapshotJson;
    private Long sourceRevisionId;
    private String sourceCourseStatus;

    private Long thumbnailMediaId;
    private String thumbnailUrl;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    private String rejectionReason;
    private Instant archivedAt;

    private String autoUpgradeOutcome;
    private Integer autoUpgradeAffectedEnrollments;
    private String autoUpgradeReasonCode;
    private String autoUpgradeReasonDetail;
}
