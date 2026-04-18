package com.exe.skillverse_backend.student_verification_service.dto.response;

import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentVerificationStartResponse {
    private Long requestId;
    private StudentVerificationStatus status;
    private LocalDateTime otpExpiresAt;
    private String message;
}
