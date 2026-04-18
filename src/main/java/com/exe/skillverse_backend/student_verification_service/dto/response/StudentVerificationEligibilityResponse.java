package com.exe.skillverse_backend.student_verification_service.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentVerificationEligibilityResponse {
    private boolean approved;
    private boolean canBuyStudentPremium;
    private String message;
    private LocalDateTime lastApprovedAt;
}
