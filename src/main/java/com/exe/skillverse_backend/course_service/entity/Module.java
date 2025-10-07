package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modules", indexes = {
        @Index(columnList = "course_id"),
        @Index(columnList = "orderIndex")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Module {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 2000)
  private String description;

  private Integer orderIndex;

  private Instant createdAt;
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Lesson> lessons = new ArrayList<>();
  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Quiz> quizzes = new ArrayList<>();
  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Assignment> assignments = new ArrayList<>();
  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<CodingExercise> codingExercises = new ArrayList<>();
}


