package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.shared.entity.Media;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

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

  /* ====== Pricing (optional) ====== */
  @Column(precision = 12, scale = 2)
  private BigDecimal price;

  @Column(length = 10)
  private String currency;

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
  /* ====== Course -> Modules ====== */
  @Builder.Default
  @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude @EqualsAndHashCode.Exclude
  private List<Module> modules = new ArrayList<>();

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
  /* ====== Helper Methods ====== */
  
  public void addModule(Module module) {
    modules.add(module);
    module.setCourse(this);
  }
  public void removeModule(Module module) {
    modules.remove(module);
    module.setCourse(null);
  }
}

