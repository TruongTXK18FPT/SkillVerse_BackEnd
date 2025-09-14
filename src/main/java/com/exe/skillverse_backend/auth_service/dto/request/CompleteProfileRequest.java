package com.exe.skillverse_backend.auth_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete profile and get tokens request")
public class CompleteProfileRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "User email address", example = "user@example.com")
    private String email;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "User's full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @Schema(description = "Address", example = "123 Main St, City, Country")
    private String address;

    @Schema(description = "Region/Country", example = "United States")
    private String region;

    @Schema(description = "Biography", example = "Software developer with 5 years experience")
    private String bio;

    @Schema(description = "Avatar media ID", example = "1")
    private Long avatarMediaId;

    @Schema(description = "Company ID", example = "1")
    private Long companyId;

    @Schema(description = "Social media links as JSON", example = "{\"linkedin\":\"...\", \"github\":\"...\"}")
    private String socialLinks;
}