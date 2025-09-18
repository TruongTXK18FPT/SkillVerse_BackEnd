package com.exe.skillverse_backend.shared.dto.request;

import com.exe.skillverse_backend.shared.validation.PasswordMatches;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@PasswordMatches
@Schema(description = "Base registration request with common fields")
public abstract class BaseRegistrationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "LOGIN EMAIL - This email will be used for user authentication and login", example = "user@gmail.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$", message = "Password must contain at least 8 characters with uppercase, lowercase, number and special character")
    @Schema(description = "User password", example = "Password123!")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Confirm password (must match password)", example = "Password123!")
    private String confirmPassword;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "User's full name", example = "Ur mum FAT!")
    private String fullName;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be valid")
    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    @Schema(description = "User biography", example = "Passionate about technology and learning")
    private String bio;

    @Size(max = 200, message = "Address must not exceed 200 characters")
    @Schema(description = "User address", example = "123 Main St, City, Country")
    private String address;

    @Size(max = 100, message = "Region must not exceed 100 characters")
    @Schema(description = "User region/location", example = "Vietnamese")
    private String region;
}