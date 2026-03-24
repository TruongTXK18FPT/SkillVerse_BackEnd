package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeType;
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
public class OpenDisputeRequest {
    @NotNull
    private Long jobId;

    private Long applicationId;

    @NotNull
    private DisputeType disputeType;

    @NotBlank
    private String reason;
}
