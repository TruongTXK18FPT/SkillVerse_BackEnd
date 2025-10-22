package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgressId implements Serializable {
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "lesson_id")
    private Long lessonId;
}
