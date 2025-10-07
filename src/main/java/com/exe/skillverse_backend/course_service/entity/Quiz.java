package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "quizzes", uniqueConstraints = @UniqueConstraint(columnNames = "module_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Quiz {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "module_id", nullable = false, unique = true)
  private Module module;

  @Column(length = 200) private String title;
  @Lob private String description;
  private Integer passScore;

  private Instant createdAt = Instant.now();
  private Instant updatedAt;

  @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<QuizQuestion> questions;
}
