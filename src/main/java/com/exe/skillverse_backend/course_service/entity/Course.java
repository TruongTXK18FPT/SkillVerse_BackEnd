package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.shared.entity.Media;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "courses",
    indexes = {
        @Index(columnList = "author_id"),
        @Index(columnList = "status")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Lob
  private String description;

  @Column(length = 50)
  private String level;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private CourseStatus status = CourseStatus.DRAFT;

  /* ====== Quan hệ User (tác giả) ====== */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  /* ====== Quan hệ Media (thumbnail) ====== */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "thumbnail_media_id")
  private Media thumbnail;

  /* ====== Timestamps ====== */
  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  /* ====== Quan hệ nội dung: Course -> Lessons ====== */
  @Builder.Default
  @OneToMany(
      mappedBy = "course",
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Lesson> lessons = new ArrayList<>();

  /* ====== Quan hệ học viên/tiến độ/giao dịch/chứng chỉ/skill ====== */
  // Course <- CourseEnrollment  (nhiều học viên ghi danh vào một course)
  @Builder.Default
  @OneToMany(mappedBy = "course")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<CourseEnrollment> enrollments = new ArrayList<>();

  // Course <- CoursePurchase    (các giao dịch mua khóa liên quan course này)
  @Builder.Default
  @OneToMany(mappedBy = "course")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<CoursePurchase> purchases = new ArrayList<>();

  // Course <- Certificate       (các chứng chỉ đã cấp thuộc course này)
  @Builder.Default
  @OneToMany(mappedBy = "course")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Certificate> certificates = new ArrayList<>();

  // Course <- CourseSkill (bảng nối N-N giữa course và skill)
  @Builder.Default
  @OneToMany(mappedBy = "course", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<CourseSkill> courseSkills = new ArrayList<>();

  /* ====== Helper để giữ đồng bộ 2 chiều ====== */
  public void addLesson(Lesson l) {
    lessons.add(l);
    l.setCourse(this);
  }

  public void removeLesson(Lesson l) {
    lessons.remove(l);
    l.setCourse(null);
  }
}

