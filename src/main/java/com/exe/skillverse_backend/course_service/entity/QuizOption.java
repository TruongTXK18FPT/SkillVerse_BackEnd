package com.exe.skillverse_backend.course_service.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  @Column(nullable = false, columnDefinition = "TEXT")
  private String optionText;

  @Column(nullable = false)
  private Boolean isCorrect;

  @Column(length = 255)
  private String feedback;

  private Integer orderIndex;
}
