package com.exe.skillverse_backend.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResponse {

    private boolean success;
    private String message;
    private String email;
    private int otpExpiryMinutes;
    private String nextStep;
}
