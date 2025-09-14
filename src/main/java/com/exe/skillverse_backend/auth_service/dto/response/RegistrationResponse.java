package com.exe.skillverse_backend.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponse {
    private String message;
    private String email;
    private boolean requiresVerification;
    private int otpExpiryMinutes;
    private String nextStep;
}