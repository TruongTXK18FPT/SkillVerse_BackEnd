package com.exe.skillverse_backend.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerifiedResponse {
    private String message;
    private String email;
    private boolean emailVerified;
    private boolean profileCompleted;
    private String nextStep;
    private String profileCompletionEndpoint;
}