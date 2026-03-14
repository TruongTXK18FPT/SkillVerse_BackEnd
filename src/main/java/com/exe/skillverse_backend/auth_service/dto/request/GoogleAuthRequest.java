package com.exe.skillverse_backend.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for receiving Google OAuth ID Token from frontend.
 * The frontend will send the ID Token obtained from Google OAuth flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    /**
     * Optional remember-me hint from client.
     * - true: longer refresh session
     * - false/null: shorter refresh session
     */
    private Boolean rememberMe = false;
}
