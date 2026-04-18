package com.exe.skillverse_backend.admin_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.Dispute;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDisputeResponse {

    private Long id;
    private Long jobId;
    private Long applicationId;
    private String jobTitle;
    private String jobStatus;
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
    private LocalDateTime adminResolutionDeadlineAt;
    private Integer escalationLevel;
    private String priority;
    private LocalDateTime escalatedAt;
    private LocalDateTime createdAt;
    private Long workerUserId;
    private String applicationStatus;
    private List<EvidenceInfo> evidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceInfo {
        private Long id;
        private Long disputeId;
        private Long submittedBy;
        private String submittedByName;
        private String evidenceType;
        private String content;
        private String fileUrl;
        private String fileName;
        private String description;
        private Boolean isOfficial;
        private LocalDateTime createdAt;
        private List<ResponseInfo> responses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseInfo {
        private Long id;
        private Long disputeId;
        private Long evidenceId;
        private Long respondedBy;
        private String respondedByName;
        private String content;
        private Boolean isAdminResponse;
        private LocalDateTime createdAt;
    }
}
