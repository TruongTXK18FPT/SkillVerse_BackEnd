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

  @Column(columnDefinition = "TEXT")
  private String submissionText;
  @Column(length = 500) private String linkUrl;

  @Column(nullable = false)
  private Instant submittedAt;

  @Column(precision = 5, scale = 2)
  private BigDecimal score;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "graded_by")
  private User gradedBy;

  @Column(columnDefinition = "TEXT")
  private String feedback;

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

  // ===== AI Grading fields =====
  @Builder.Default
  @Column(name = "is_ai_graded", nullable = false)
  private Boolean isAiGraded = false;

  @Column(name = "ai_graded_at")
  private Instant aiGradedAt;

  @Column(name = "ai_score", precision = 10, scale = 2)
  private BigDecimal aiScore;

  @Column(name = "ai_feedback", columnDefinition = "TEXT")
  private String aiFeedback;

  @Column(name = "ai_confidence")
  private Double aiConfidence; // 0.0 to 1.0

  @Column(name = "mentor_confirmed")
  private Boolean mentorConfirmed;

  @Builder.Default
  @Column(name = "ai_grade_attempt_count", nullable = false)
  private Integer aiGradeAttemptCount = 0;

  @Builder.Default
  @Column(name = "dispute_flag", nullable = false)
  private Boolean disputeFlag = false;

  @Column(name = "dispute_at")
  private Instant disputeAt;

  @Column(name = "dispute_reason", columnDefinition = "TEXT")
  private String disputeReason;

  /**
   * Grading mode for this submission.
   * null/AI = AI grading (default); MENTOR = skip AI, go straight to mentor queue.
   */
  @Column(name = "grading_mode", length = 10)
  @Builder.Default
  private String gradingMode = "AI";
}
