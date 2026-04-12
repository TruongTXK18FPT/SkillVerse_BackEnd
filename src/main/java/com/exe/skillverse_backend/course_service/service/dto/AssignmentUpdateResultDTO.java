package com.exe.skillverse_backend.course_service.service.dto;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentDetailDTO;

public record AssignmentUpdateResultDTO(
    AssignmentDetailDTO assignment,
    long gradedSubmissionCount,
    long aiPendingSubmissionCount,
    long pendingSubmissionCount
) {}
