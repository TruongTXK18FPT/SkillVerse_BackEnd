package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor @Builder
class ModuleProgressId implements Serializable {
  @Column(name = "user_id") private Long userId;
  @Column(name = "module_id") private Long moduleId;
}

@Entity @Table(name = "module_progress",
  indexes = @Index(columnList = "user_id, module_id", unique = true))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ModuleProgress {
  @EmbeddedId
  private ModuleProgressId id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("userId")
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("moduleId")
  @JoinColumn(name = "module_id", nullable = false)
  private Module module;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProgressStatus status = ProgressStatus.IN_PROGRESS;

  @Builder.Default
  @Column(nullable = false) private Integer timeSpentSec = 0;
  private Integer lastPositionSec;

  @Builder.Default
  @Column(nullable = false) private Instant updatedAt = Instant.now();
}
