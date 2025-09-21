package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity @Table(name = "quiz_questions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizQuestion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Lob @Column(nullable = false)
  private String questionText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QuestionType questionType;

  @Column(nullable = false) private Integer score = 1;
  private Integer orderIndex;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<QuizOption> options;
}
