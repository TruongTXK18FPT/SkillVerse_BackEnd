package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Business/Recruiter registration request")
public class BusinessRegistrationRequest extends BaseRegistrationRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name must not exceed 200 characters")
    @Schema(description = "Company or organization name", example = "Tech Solutions Inc.")
    private String companyName;

    @NotBlank(message = "Company website is required")
    @Size(max = 200, message = "Company website must not exceed 200 characters")
    @Schema(description = "Company website URL", example = "https://www.techsolutions.com")
    private String companyWebsite;

    @NotBlank(message = "Company address is required")
    @Size(max = 500, message = "Company address must not exceed 500 characters")
    @Schema(description = "Complete company address", example = "123 Business District, Ho Chi Minh City, Vietnam")
    private String companyAddress;

    @NotBlank(message = "Tax code or business registration number is required")
    @Size(max = 50, message = "Tax code must not exceed 50 characters")
    @Pattern(regexp = "^[0-9]{10}$|^[0-9]{13}$", message = "Tax code must be exactly 10 or 13 digits")
    @Schema(description = "Tax code or business registration number (10 digits for main office, 13 digits for branches)", example = "0123456789")
    private String taxCodeOrBusinessRegistrationNumber;

    @NotBlank(message = "Company documents URL is required")
    @Size(max = 500, message = "Company documents URL must not exceed 500 characters")
    @Schema(description = "URL to uploaded company documents (business license, tax certificate, etc.)", example = "https://storage.example.com/company-docs/license.pdf")
    private String companyDocumentsUrl;

    // Contact Person Information
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be valid (10-15 digits)")
    @Schema(description = "Contact person phone number (optional)", example = "+84912345678")
    private String contactPersonPhone;

    @NotBlank(message = "Contact person position is required")
    @Size(max = 100, message = "Position must not exceed 100 characters")
    @Schema(description = "Contact person position/title", example = "CEO")
    private String contactPersonPosition;

    // Company Extended Information
    @NotBlank(message = "Company size is required")
    @Size(max = 50, message = "Company size must not exceed 50 characters")
    @Schema(description = "Company size range", example = "11-50")
    private String companySize;

    @NotBlank(message = "Industry is required")
    @Size(max = 200, message = "Industry must not exceed 200 characters")
    @Schema(description = "Company industry/sector", example = "Information Technology")
    private String industry;
}