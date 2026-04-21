package com.exe.skillverse_backend.mentor_verification_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [Nghiệp vụ] DTO để admin review (approve/reject) yêu cầu xác thực skill mentor.
 * approved = true → mentor được công nhận skill đó.
 * approved = false → reject, admin phải ghi lý do.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewMentorVerificationRequest {

    @NotNull(message = "Approved status is required")
    private Boolean approved;

    @Size(max = 2000, message = "Review note must be at most 2000 characters")
    private String reviewNote;
}
