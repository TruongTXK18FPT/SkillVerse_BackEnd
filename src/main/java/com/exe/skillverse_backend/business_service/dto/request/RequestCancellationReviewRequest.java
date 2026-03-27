package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestCancellationReviewRequest {

    @NotNull
    private Long applicationId;

    @NotBlank
    private String reason;
}
