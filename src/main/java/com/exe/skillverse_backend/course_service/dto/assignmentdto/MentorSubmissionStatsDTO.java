package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorSubmissionStatsDTO {
    private long totalCount;
    private long pendingCount;
    private long gradedCount;
    private long lateCount;
}
