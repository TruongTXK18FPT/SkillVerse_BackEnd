package com.exe.skillverse_backend.course_service.entity;

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
import java.time.Instant;
import java.util.List;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;

@Entity
@Table(name = "quizzes")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Quiz {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "module_id", nullable = false)
  private Module module;

  @Column(length = 200) private String title;
  @Lob private String description;
  private Integer passScore;
  private Integer maxAttempts;
  @Column(name = "time_limit_minutes")
  private Integer timeLimitMinutes;
  @Column(name = "rounding_increment")
  private Integer roundingIncrement;
  @Enumerated(EnumType.STRING)
  @Column(name = "grading_method", length = 20)
  private QuizGradingMethod gradingMethod;
  @Column(name = "is_assessment")
  private Boolean isAssessment;
  @Column(name = "cooldown_hours")
  private Integer cooldownHours;
  @Column(name = "order_index")
  private Integer orderIndex;

  private Instant createdAt = Instant.now();
  private Instant updatedAt;

  @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<QuizQuestion> questions;
}
