package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO cho recruitment session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentSessionResponse {

    private Long id;

    // Recruiter info
    private Long recruiterId;
    private String recruiterName;
    private String recruiterAvatar;
    private String recruiterCompany;

    // Candidate info
    private Long candidateId;
    private String candidateFullName;
    private String candidateTitle;
    private String candidateAvatar;
    private Boolean candidateHasPortfolio;
    private String candidateSlug;

    // Job info (nếu có)
    private Long jobId;
    private String jobTitle;
    private RecruitmentJobContextType jobContextType;
    private String jobStatus;
    private Boolean isRemote;
    private String jobLocation;
    private Boolean isChatAvailable;
    private String chatDisabledReason;

    // Session metadata
    private RecruitmentSessionStatus status;
    private RecruitmentSessionSource sourceType;
    private Integer matchScore;
    private Integer skillMatchPercent;

    // Unread counts
    private Integer unreadCount;

    // Timestamps
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Last message preview
    private String lastMessagePreview;
}
