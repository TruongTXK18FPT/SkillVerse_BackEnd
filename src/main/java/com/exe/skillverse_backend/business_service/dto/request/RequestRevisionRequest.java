package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request để yêu cầu revision
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestRevisionRequest {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotBlank(message = "Revision note is required")
    @Size(max = 5000, message = "Revision note must not exceed 5000 characters")
    private String note;

    private List<String> specificIssues;
}
