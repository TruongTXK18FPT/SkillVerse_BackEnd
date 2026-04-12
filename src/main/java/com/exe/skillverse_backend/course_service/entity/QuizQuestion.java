package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
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
import java.util.List;

@Entity @Table(name = "quiz_questions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizQuestion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String questionText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QuestionType questionType;

  @Column(nullable = false) private Integer score = 1;
  private Integer orderIndex;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<QuizOption> options;
}
