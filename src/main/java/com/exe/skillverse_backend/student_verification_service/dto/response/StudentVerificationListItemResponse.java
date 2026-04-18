package com.exe.skillverse_backend.student_verification_service.dto.response;

import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentVerificationListItemResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String schoolEmail;
    private StudentVerificationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
