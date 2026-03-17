package com.exe.skillverse_backend.course_service.entity;

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
