package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.Dispute;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubmittedDisputeResponse {

    private Long id;
    private Long jobId;
    private Long applicationId;
    private String jobTitle;
    private String jobStatus;
    private String applicationStatus;
    private Long initiatorId;
    private String initiatorName;
    private Long respondentId;
    private String respondentName;
    private Dispute.DisputeType disputeType;
    private String reason;
    private Dispute.DisputeStatus status;
    private Dispute.DisputeResolution resolution;
    private BigDecimal partialRefundPct;
    private String resolutionNotes;
    private Long resolvedBy;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime adminResolutionDeadlineAt;
    private Integer escalationLevel;
    private String priority;
}
