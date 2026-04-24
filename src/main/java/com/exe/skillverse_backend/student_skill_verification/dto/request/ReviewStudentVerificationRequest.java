package com.exe.skillverse_backend.student_skill_verification.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [Nghiệp vụ] DTO để admin duyệt/reject yêu cầu xác thực skill student.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewStudentVerificationRequest {

    @NotNull(message = "Approved flag is required")
    private Boolean approved;

    private String reviewNote;
}
