package com.exe.skillverse_backend.course_service.dto.progressdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseLearningRevisionInfoDTO {
    private Long courseId;
    private Long userId;
    private Long learningRevisionId;
    private Long activeRevisionId;
    private Long latestRevisionId;
    private String upgradePolicy;
    private boolean hasNewerRevision;
}
