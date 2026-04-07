package com.exe.skillverse_backend.portfolio_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for completed short-term job applications
 * shown in the portfolio "Nhiệm vụ đã hoàn thành" tab.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletedMissionDTO {

    private Long applicationId;
    private Long jobId;
    private String jobTitle;
    private String jobDescription;
    private String recruiterName;
    private String recruiterAvatar;
    private String recruiterCompanyName;
    private BigDecimal budget;
    private String currency;
    private LocalDate deadline;
    private String estimatedDuration;
    private Boolean isRemote;
    private String location;
    private List<String> requiredSkills;
    private String paymentMethod;
    private LocalDateTime completedAt;
    private Double rating;
    private String reviewComment;
    // Detailed rating breakdown
    private Integer communicationRating;
    private Integer qualityRating;
    private Integer timelinessRating;
    private Integer professionalismRating;
    // Deliverables
    private List<DeliverableInfo> deliverables;
    private String status; // "COMPLETED" | "PAID"
    private String workNote;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverableInfo {
        private String fileName;
        private String fileUrl;
        private String type;
    }
}
