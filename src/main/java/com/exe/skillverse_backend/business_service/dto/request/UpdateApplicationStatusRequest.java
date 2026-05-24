package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApplicationStatusRequest {

    @NotNull(message = "Application status is required")
    private JobApplicationStatus status;

    private String acceptanceMessage; // Required if status = ACCEPTED

    private String rejectionReason; // Required if status = REJECTED

    private String interviewResult; // Optional — interview notes when marking INTERVIEWED

    private String offerDetails; // Optional — offer letter content when sending OFFER_SENT

    // Recruiter's structured offer fields
    private Long offerSalary; // Offered salary amount (VND)
    private String offerAdditionalRequirements; // Additional terms/benefits/conditions

    // Candidate's response after receiving OFFER_SENT
    private String candidateOfferResponse; // Optional — counter-offer or acceptance message

    // Candidate's structured counter-offer fields
    private Long counterSalaryAmount; // Counter salary amount requested by candidate
    private String counterAdditionalRequirements; // Additional requirements from candidate
}
