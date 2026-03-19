package com.exe.skillverse_backend.course_service.dto.progressdto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseLearningStatusDTO {

    private Long courseId;
    private Long userId;

    private List<Long> completedLessonIds;
    private List<Long> completedQuizIds;
    private List<Long> completedAssignmentIds;

    private long completedLessonCount;
    private long totalLessonCount;

    private long completedQuizCount;
    private long totalQuizCount;

    private long completedRequiredAssignmentCount;
    private long totalRequiredAssignmentCount;

    private long completedItemCount;
    private long totalItemCount;

    private int percent;
    private Long certificateId;
    private String certificateSerial;
    private Boolean certificateRevoked;
    private Instant certificateRevokedAt;
}
