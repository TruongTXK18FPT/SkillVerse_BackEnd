package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
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

  private Instant dueAt;
  @Builder.Default
  private Instant createdAt = Instant.now();
  private Instant updatedAt;

  @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<AssignmentSubmission> submissions;
}
