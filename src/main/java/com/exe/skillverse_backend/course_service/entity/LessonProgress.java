package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor @Builder
class LessonProgressId implements Serializable {
  @Column(name = "user_id") private Long userId;
  @Column(name = "lesson_id") private Long lessonId;
}

@Entity @Table(name = "lesson_progress",
  indexes = @Index(columnList = "user_id, lesson_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LessonProgress {
  @EmbeddedId
  private LessonProgressId id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("userId")
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("lessonId")
  @JoinColumn(name = "lesson_id", nullable = false)
  private Lesson lesson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProgressStatus status = ProgressStatus.IN_PROGRESS;

  @Column(nullable = false) private Integer timeSpentSec = 0;
  private Integer lastPositionSec;

  @Column(nullable = false) private Instant updatedAt = Instant.now();
}
