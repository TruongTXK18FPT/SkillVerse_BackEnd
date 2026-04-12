package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EntitlementSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "course_enrollment", indexes = { @Index(columnList = "course_id, status"),
    @Index(columnList = "user_id, course_id") })
@Getter
@Setter
@ToString(exclude = {"user", "course"})
@EqualsAndHashCode(exclude = {"user", "course"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEnrollment {
  @Embeddable
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CourseEnrollmentId implements Serializable {
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "course_id")
    private Long courseId;
  }

  @EmbeddedId
  private CourseEnrollmentId id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("userId")
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("courseId")
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Builder.Default
  @Column(nullable = false)
  private Instant enrollDate = Instant.now();

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

  @Builder.Default
  @Column(nullable = false)
  private Integer progressPercent = 0;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EntitlementSource entitlementSource = EntitlementSource.PURCHASE;

  @Column(length = 64)
  private String entitlementRef;

  @Column(name = "learning_revision_id")
  private Long learningRevisionId;

  @Column(name = "upgrade_policy_snapshot", length = 32)
  private String upgradePolicySnapshot;

  @Column(name = "last_upgraded_at")
  private Instant lastUpgradedAt;

  /**
   * Timestamp when enrollment was marked as COMPLETED.
   * Used instead of lastUpgradedAt to preserve real completion date.
   * Schema migration: DatabaseSchemaFixer auto-adds this nullable column.
   */
  @Column(name = "completed_at")
  private Instant completedAt;

  // Ensure composite key is populated automatically from relations
  @PrePersist
  void prePersist() {
    if (this.id == null && this.user != null && this.course != null) {
      this.id = new CourseEnrollmentId(this.user.getId(), this.course.getId());
    }
  }
}
