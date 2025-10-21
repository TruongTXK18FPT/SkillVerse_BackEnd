package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EntitlementSource;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "course_enrollment", indexes = { @Index(columnList = "course_id, status"),
    @Index(columnList = "user_id, course_id") })
@Data
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

  // Ensure composite key is populated automatically from relations
  @PrePersist
  void prePersist() {
    if (this.id == null && this.user != null && this.course != null) {
      this.id = new CourseEnrollmentId(this.user.getId(), this.course.getId());
    }
  }
}
