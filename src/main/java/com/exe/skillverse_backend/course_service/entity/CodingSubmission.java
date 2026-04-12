package com.exe.skillverse_backend.course_service.entity;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.CodeSubmissionStatus;
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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "coding_submissions",
  indexes = { @Index(columnList = "exercise_id, user_id"), @Index(columnList = "status") })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CodingSubmission {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "exercise_id", nullable = false)
  private CodingExercise exercise;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String submittedCode;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CodeSubmissionStatus status = CodeSubmissionStatus.QUEUED;

  @Column(precision = 5, scale = 2) private BigDecimal score;
  @Column(columnDefinition = "TEXT")
  private String feedback;

  @Builder.Default
  @Column(nullable = false)
  private Instant submittedAt = Instant.now();
}
