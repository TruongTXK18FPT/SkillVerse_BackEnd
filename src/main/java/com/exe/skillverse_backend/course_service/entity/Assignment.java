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

  @Lob private String description;

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

  @Lob
  @Column(name = "learning_outcome")
  private String learningOutcome; // What students will learn

  @Lob
  @Column(name = "grading_criteria")
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
}
