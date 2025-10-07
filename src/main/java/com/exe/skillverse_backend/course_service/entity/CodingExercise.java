package com.exe.skillverse_backend.course_service.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "coding_exercises", uniqueConstraints = @UniqueConstraint(columnNames = "module_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CodingExercise {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "module_id", nullable = false, unique = true)
  private Module module;

  @Column(nullable = false, length = 200)
  private String title;

  @Lob @Column(nullable = false)
  private String prompt;

  @Column(nullable = false, length = 50)
  private String language; // Python/Java/JS...

  @Lob private String starterCode;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal maxScore;

  @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<CodingTestCase> testCases;
}
