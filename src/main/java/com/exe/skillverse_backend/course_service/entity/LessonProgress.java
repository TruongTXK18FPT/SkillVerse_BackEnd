package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

// ID moved to public file LessonProgressId

@Entity
@Table(name = "lesson_progress", indexes = {
        @Index(columnList = "user_id, lesson_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgress {

    @EmbeddedId
    private LessonProgressId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("lessonId")
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false;

    private Instant completedAt;
}
