package com.exe.skillverse_backend.course_service.entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
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

  @Column(nullable = false, columnDefinition = "TEXT")
  private String prompt;

  @Column(nullable = false, length = 50)
  private String language; // Python/Java/JS...

  @Column(columnDefinition = "TEXT")
  private String starterCode;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal maxScore;

  @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<CodingTestCase> testCases;
}
