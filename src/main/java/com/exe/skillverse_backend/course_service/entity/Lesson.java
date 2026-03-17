package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.List;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.shared.entity.Media;

@Entity
@Table(name = "lessons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Lesson belongs to a module
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "module_id", nullable = false)
  private Module module;

  @Column(nullable = false, length = 200)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LessonType type;

  private Integer orderIndex;

  @Lob
  private String contentText; // READING
  @Column(length = 500)
  private String resourceUrl; // READING link
  @Column(length = 500)
  private String videoUrl;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "video_media_id")
  private Media videoMedia;
  private Integer durationSec;

  @Builder.Default
  private Instant createdAt = Instant.now();
  private Instant updatedAt;

  // ✅ NEW: Attachments for Reading lessons (PDFs, links, etc.)
  @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @Builder.Default
  private List<LessonAttachment> attachments = new ArrayList<>();

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
