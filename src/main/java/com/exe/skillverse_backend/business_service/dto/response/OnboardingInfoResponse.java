package com.exe.skillverse_backend.business_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Response DTO for onboarding info — returns what was saved (text only, no images).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingInfoResponse {

    private Long applicationId;
    private String status; // Current application status after submit

    // CCCD info
    private String idCardNumber;
    private String fullName;
    private String dateOfBirth;
    private LocalDate idCardDate;
    private String idCardPlace;
    private String address;

    // Bank info
    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolder;
}
