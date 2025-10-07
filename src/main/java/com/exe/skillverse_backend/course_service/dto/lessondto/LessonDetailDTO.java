package com.exe.skillverse_backend.course_service.dto.lessondto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDetailDTO {
    private Long id;
    private String title;
    private String type;
    private Integer orderIndex;
    private Integer durationSec;
    private String contentText;
    private String videoUrl;
    private Long videoMediaId;
}


