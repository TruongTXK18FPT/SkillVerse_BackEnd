package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.shared.entity.Media;
@Entity @Table(name = "lessons")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Lesson {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false, length = 200)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LessonType type;

  private Integer orderIndex;

  @Lob private String contentText;          // READING
  @Column(length = 500) private String videoUrl;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "video_media_id")
  private Media videoMedia;
  private Integer durationSec;

  private Instant createdAt = Instant.now();
  private Instant updatedAt;

  /** quan hệ 1–1 với đánh giá/bài tập/codelab (owner ở phía kia) */
  @OneToOne(mappedBy = "lesson", fetch = FetchType.LAZY)
  private Quiz quiz;

  @OneToOne(mappedBy = "lesson", fetch = FetchType.LAZY)
  private Assignment assignment;

  @OneToOne(mappedBy = "lesson", fetch = FetchType.LAZY)
  private CodingExercise codelab;
}
