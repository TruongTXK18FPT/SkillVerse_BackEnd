package com.exe.skillverse_backend.mentor_verification_service.dto.response;

import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchVerificationResponse {

    private Long id;
    private Long mentorId;
    private String mentorName;
    private String mentorEmail;
    private String mentorAvatarUrl;

    private VerificationStatus status;
    private String githubUrl;
    private String portfolioUrl;
    private String additionalNotes;
    private String generalReviewNote;

    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    private List<MentorVerificationResponse.EvidenceResponse> evidences;
    private List<MentorVerificationResponse> skills;
}
