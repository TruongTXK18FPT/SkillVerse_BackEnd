package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizDetailDTO {
    private Long id;
    private String title;
    private String description;
    private Integer passScore;
    private Long moduleId;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
    List<QuizQuestionDetailDTO> questions;
}
