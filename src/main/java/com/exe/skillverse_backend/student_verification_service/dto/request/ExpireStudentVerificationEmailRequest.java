package com.exe.skillverse_backend.student_verification_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpireStudentVerificationEmailRequest {

    @NotBlank(message = "Expiration reason is required")
    @Size(max = 1000, message = "Expiration reason is too long")
    private String reason;
}
