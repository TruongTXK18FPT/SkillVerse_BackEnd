package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyJobRequest {

    @Size(max = 1000, message = "Cover letter must not exceed 1000 characters")
    private String coverLetter; // Optional cover letter
}
