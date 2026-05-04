package com.exe.skillverse_backend.business_service.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured description of a single blocker preventing a job from being closed.
 * Surfaced to the recruiter so they know exactly which applicant/contract needs action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobFlowBlockingItem {
    /** Scope of the blocker: APPLICATION or CONTRACT */
    private String scope;

    /** Application id (always present if scope=APPLICATION; also present for scope=CONTRACT when resolvable). */
    private Long applicationId;

    /** Contract id (only for scope=CONTRACT). */
    private Long contractId;

    /** Display name of the applicant (best effort). */
    private String applicantName;

    /** Applicant email (best effort). */
    private String applicantEmail;

    /** Raw current status (JobApplicationStatus or ContractStatus name). */
    private String currentStatus;

    /** Human-readable instruction for the recruiter. */
    private String requiredAction;
}
