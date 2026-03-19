package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
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
public class ShortTermApplicationResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private BigDecimal jobBudget;

    // User info
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String userAvatar;
    private BigDecimal userRating;
    private Integer userCompletedJobs;

    // Application details
    private String coverLetter;
    private BigDecimal proposedPrice;
    private String proposedDuration;
    private List<String> portfolio;

    // Status
    private ShortTermApplicationStatus status;

    // Timestamps
    private LocalDateTime appliedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    // Work submission
    private List<DeliverableResponse> deliverables;
    private String workNote;

    // Revision
    private Integer revisionCount;
    private List<RevisionNoteResponse> revisionNotes;

    // Job info (optional, for candidate view)
    private JobInfo jobDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverableResponse {
        private Long id;
        private String type;
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private String mimeType;
        private String description;
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevisionNoteResponse {
        private Long id;
        private String note;
        private List<String> specificIssues;
        private Long requestedById;
        private String requestedByName;
        private LocalDateTime requestedAt;
        private LocalDateTime resolvedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobInfo {
        private String title;
        private BigDecimal budget;
        private LocalDateTime deadline;
        private String recruiterCompanyName;
    }
}
