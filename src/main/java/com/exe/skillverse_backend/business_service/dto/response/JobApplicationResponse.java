package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
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
public class JobApplicationResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String userAvatar;
    private String userProfessionalTitle;
    private String coverLetter;
    private LocalDateTime appliedAt;
    private JobApplicationStatus status;
    private String acceptanceMessage;
    private String rejectionReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime processedAt;
    private String interviewResult; // Interview notes after INTERVIEWED status

    // Offer letter fields
    private String offerDetails; // Recruiter's additional conditions when status = OFFER_SENT
    private Long offerSalary; // Recruiter's offered salary amount (VND)
    private String offerAdditionalRequirements; // Recruiter's additional terms/benefits
    private String candidateOfferResponse; // Candidate's counter-offer/acceptance when status = OFFER_ACCEPTED/REJECTED
    private Long counterSalaryAmount; // Candidate's counter salary amount (VND)
    private String counterAdditionalRequirements; // Candidate's additional requirements
    private Integer offerRound; // Current offer round (1 or 2); 0 means no offer sent yet

    // Job details for user's application view
    private String recruiterCompanyName;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private Boolean isRemote;
    private String location;
    private Boolean isNegotiable; // Whether the job has negotiable salary

    // Premium feature
    private Boolean isHighlighted;

    // Portfolio link
    private String portfolioSlug;

    // Contract link
    private Long contractId;
    private String contractStatus;
}
