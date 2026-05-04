package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Learner submission for the optional final output assessment step.
 * Only relevant when journey.journeyOutputVerificationRequired = true.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitJourneyOutputAssessmentRequest {

    @NotBlank
    private String submissionText;

    @Size(max = 1000)
    private String evidenceUrl;

    private String evidencePublicId;
    private String evidenceResourceType;

    @Size(max = 1000)
    private String attachmentUrl;

    private String attachmentPublicId;
    private String attachmentResourceType;
}
