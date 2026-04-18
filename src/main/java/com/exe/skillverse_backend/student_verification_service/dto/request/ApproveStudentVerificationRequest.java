package com.exe.skillverse_backend.student_verification_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveStudentVerificationRequest {

    @Size(max = 1000, message = "Review note is too long")
    private String reviewNote;
}
