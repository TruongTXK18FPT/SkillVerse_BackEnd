package com.exe.skillverse_backend.admin_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewBookingDisputeEvidenceRequest {

    private EvidenceReviewDecision decision;
    private String notes;

    public enum EvidenceReviewDecision {
        MARK_UNDER_REVIEW,
        ACCEPT_EVIDENCE_REFUND_USER,
        REJECT_EVIDENCE_RELEASE_MENTOR
    }
}
