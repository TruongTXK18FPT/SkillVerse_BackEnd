package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeVerification.NodeVerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mentor verification decision for a node submission.
 * Can only be invoked AFTER at least one APPROVED review exists on the submission.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyNodeRequest {

    @NotNull
    private NodeVerificationStatus nodeVerificationStatus;

    private String verificationNote;

    private Long bookingId;
}
