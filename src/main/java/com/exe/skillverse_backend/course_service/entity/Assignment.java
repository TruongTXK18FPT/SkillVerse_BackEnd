package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Assignment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "module_id", nullable = false)
  private Module module;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SubmissionType submissionType; // FILE/TEXT/LINK

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal maxScore;

  @Column(name = "passing_score", precision = 5, scale = 2)
  private BigDecimal passingScore; // Minimum score required to pass

  @Column(name = "order_index")
  private Integer orderIndex; // For ordering assignments within a module

  @Builder.Default
  @Column(name = "is_required", nullable = false)
  private Boolean isRequired = true; // Required or optional assignment

  @Column(name = "learning_outcome", columnDefinition = "TEXT")
  private String learningOutcome; // What students will learn

  @Column(name = "grading_criteria", columnDefinition = "TEXT")
  private String gradingCriteria; // How it will be graded

  private Instant dueAt;
  @Builder.Default
  private Instant createdAt = Instant.now();
  private Instant updatedAt;

  @Builder.Default
  @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<AssignmentSubmission> submissions = new ArrayList<>();

  @Builder.Default
  @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<AssignmentCriteria> criteria = new ArrayList<>();

  // ===== AI Grading fields =====
  @Builder.Default
  @Column(name = "ai_grading_enabled", nullable = false)
  private Boolean aiGradingEnabled = false;

  @Column(name = "ai_grading_prompt", columnDefinition = "TEXT")
  private String aiGradingPrompt;

  @Column(name = "grading_style", length = 20)
  @Builder.Default
  private String gradingStyle = "STANDARD"; // STANDARD | STRICT | LENIENT

  @Builder.Default
  @Column(name = "trust_ai_enabled", nullable = false)
  private Boolean trustAiEnabled = false;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public SubmissionType getSubmissionType() { return submissionType; }
  public void setSubmissionType(SubmissionType submissionType) { this.submissionType = submissionType; }
  public BigDecimal getMaxScore() { return maxScore; }
  public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
  public BigDecimal getPassingScore() { return passingScore; }
  public void setPassingScore(BigDecimal passingScore) { this.passingScore = passingScore; }
}
