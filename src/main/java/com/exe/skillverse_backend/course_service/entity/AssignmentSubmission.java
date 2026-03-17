package com.exe.skillverse_backend.course_service.entity;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.entity.Media; 

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity @Table(name = "assignment_submissions",
  indexes = { 
    @Index(columnList = "assignment_id, user_id"), 
    @Index(columnList = "user_id"),
    @Index(columnList = "assignment_id, is_newest")
  })
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

  // ===== Version tracking fields (Coursera pattern: keep newest + last) =====
  
  /** Attempt number, starting from 1 */
  @Builder.Default
  @Column(name = "attempt_number", nullable = false)
  private Integer attemptNumber = 1;
  
  /** True if this is the newest (current) submission for this user */
  @Builder.Default
  @Column(name = "is_newest", nullable = false)
  private Boolean isNewest = true;
  
  /** True if this is the previous submission (kept for comparison) */
  @Builder.Default
  @Column(name = "is_previous", nullable = false)
  private Boolean isPrevious = false;
  
  /** True if submission was made after deadline */
  @Builder.Default
  @Column(name = "is_late", nullable = false)
  private Boolean isLate = false;
  
  /** Timestamp when grading was completed */
  @Column(name = "graded_at")
  private Instant gradedAt;

  /**
   * Criteria-based pass/fail result, persisted at grading time.
   * NULL = not yet graded, TRUE = passed, FALSE = failed.
   * Once set, immune to subsequent criteria/passingPoints edits.
   */
  @Column(name = "is_passed")
  private Boolean isPassed;
}
