package com.exe.skillverse_backend.course_service.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity 
@Table(name = "quiz_options")
@Data 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class QuizOption {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private QuizQuestion question;

  @Lob @Column(nullable = false)
  private String optionText;

  @Column(nullable = false)
  private Boolean isCorrect;

  @Column(length = 255)
  private String feedback;
}
