package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterProfileUpdateRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Company website is required")
    @Pattern(regexp = "^https?://.*", message = "Company website must be a valid URL")
    private String companyWebsite;

    @NotBlank(message = "Company address is required")
    private String companyAddress;

    @NotBlank(message = "Tax code or business registration number is required")
    private String taxCodeOrBusinessRegistrationNumber;

    private String companyDocumentsUrl; // Optional for updates
}
