package com.exe.skillverse_backend.course_service.dto.quizdto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizUpdateDTO {
    //String title, String description, Integer passScore
    private String title;
    private String description;
    private Integer passScore;
}
