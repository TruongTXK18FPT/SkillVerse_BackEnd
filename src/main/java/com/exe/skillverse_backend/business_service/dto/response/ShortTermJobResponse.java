package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.JobUrgency;
import com.exe.skillverse_backend.business_service.entity.enums.PaymentMethod;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
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
public class ShortTermJobResponse {

    private Long id;
    private String title;
    private String description;
    private List<String> requiredSkills;
    private String primarySkill;

    // Pricing
    private BigDecimal budget;
    private Boolean isNegotiable;
    private PaymentMethod paymentMethod;

    // Timing
    private LocalDateTime deadline;
    private String estimatedDuration;
    private JobUrgency urgency;
    private LocalDateTime startTime;

    // Work settings
    private Boolean isRemote;
    private String location;
    private Boolean isHighlighted;

    // Status
    private ShortTermJobStatus status;
    private Integer applicantCount;
    private Long selectedApplicantId;

    // Requirements
    private Integer maxApplicants;
    private BigDecimal minRating;

    // Recruiter info
    private Long recruiterId;
    private RecruiterInfo recruiterInfo;

    // Milestones
    private List<MilestoneResponse> milestones;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime completedAt;
    private LocalDateTime paidAt;

    // Computed fields
    private Boolean isExpired;
    private Boolean canApply;

    // Admin ban/flag fields
    private Boolean isBanned;
    private String banReason;
    private LocalDateTime bannedAt;
    private Long bannedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecruiterInfo {
        private Long id;
        private String companyName;
        private String companyLogoUrl;
        private BigDecimal rating;
        private Integer totalJobsPosted;
        private BigDecimal completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneResponse {
        private Long id;
        private String title;
        private String description;
        private BigDecimal amount;
        private LocalDateTime deadline;
        private String status;
        private Integer order;
        private LocalDateTime completedAt;
        private List<DeliverableResponse> deliverables;
    }

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
        private Long uploadedById;
        private String uploadedByName;
    }
}
