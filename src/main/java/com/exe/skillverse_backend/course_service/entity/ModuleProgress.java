package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

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
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

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
