package com.exe.skillverse_backend.shared.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Base registration response with common fields")
public abstract class BaseRegistrationResponse {

    @Schema(description = "Registration success status", example = "true")
    private boolean success;

    @Schema(description = "Response message", example = "Registration successful")
    private String message;

    @Schema(description = "User email address", example = "user@example.com")
    private String email;

    @Schema(description = "User ID from auth service", example = "123")
    private Long userId;

    @Schema(description = "Whether email verification is required", example = "true")
    private boolean requiresVerification;

    @Schema(description = "OTP expiry time in minutes", example = "5")
    private int otpExpiryMinutes;

    @Schema(description = "Exact OTP expiry timestamp", example = "2024-01-01T12:05:00")
    private LocalDateTime otpExpiryTime;

    @Schema(description = "Next step instruction", example = "Check your email for verification code")
    private String nextStep;
}