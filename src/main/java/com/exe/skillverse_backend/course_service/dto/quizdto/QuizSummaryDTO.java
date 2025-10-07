package com.exe.skillverse_backend.course_service.dto.quizdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSummaryDTO {
    
    private Long id;
    
    private String title;
    
    private String description;
    
    private Integer passScore;
    
    private Integer questionCount;
    
    private Instant createdAt;
    
    private Instant updatedAt;
}
