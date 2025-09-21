package com.exe.skillverse_backend.course_service.entity;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.CodeSubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
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

  @Lob @Column(nullable = false)
  private String submittedCode;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CodeSubmissionStatus status = CodeSubmissionStatus.QUEUED;

  @Column(precision = 5, scale = 2) private BigDecimal score;
  @Lob private String feedback;

  @Builder.Default
  @Column(nullable = false)
  private Instant submittedAt = Instant.now();
}
