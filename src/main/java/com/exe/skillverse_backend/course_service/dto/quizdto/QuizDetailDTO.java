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
    private Integer passScore;
    List<QuizQuestionDetailDTO> questions;
}
