package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "coding_test_cases",
  indexes = @Index(columnList = "exercise_id, order_index"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CodingTestCase {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "exercise_id", nullable = false)
  private CodingExercise exercise;

  @Column(nullable = false, length = 10)
  private String kind; // PUBLIC/HIDDEN

  @Lob @Column(nullable = false) private String input;
  @Lob @Column(nullable = false) private String expectedOutput;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal scoreWeight;

  @Column(name = "order_index")
  private Integer orderIndex;
}
