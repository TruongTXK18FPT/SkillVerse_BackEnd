package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

  /**
   * IDs of modules that must be completed BEFORE this module can be attempted.
   * Enables topological-sort-based prerequisite ordering in module distribution.
   *
   * <p>Empty list = no prerequisites (can be started immediately).
   */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "module_prerequisites", joinColumns = @JoinColumn(name = "module_id"))
  @Column(name = "prerequisite_module_id")
  @Builder.Default
  private List<Long> prerequisiteModuleIds = new ArrayList<>();

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
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Lesson> lessons = new ArrayList<>();
  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Quiz> quizzes = new ArrayList<>();
  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private Set<Assignment> assignments = new LinkedHashSet<>();
  @Builder.Default
  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude @EqualsAndHashCode.Exclude
  // LEGACY: codingExercises là code cũ, hiện chỉ dùng assignments/quizzes/lessons
  private Set<CodingExercise> codingExercises = new LinkedHashSet<>();
}


