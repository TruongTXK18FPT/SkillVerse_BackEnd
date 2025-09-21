package com.exe.skillverse_backend.course_service.entity;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.entity.Media; 

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "assignment_submissions",
  indexes = { @Index(columnList = "assignment_id, user_id"), @Index(columnList = "user_id") })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AssignmentSubmission {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assignment_id", nullable = false)
  private Assignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "file_media_id")
  private Media fileMedia;

  @Lob private String submissionText;
  @Column(length = 500) private String linkUrl;

  @Column(nullable = false)
  private Instant submittedAt;

  @Column(precision = 5, scale = 2)
  private BigDecimal score;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "graded_by")
  private User gradedBy;

  @Lob private String feedback;
}
