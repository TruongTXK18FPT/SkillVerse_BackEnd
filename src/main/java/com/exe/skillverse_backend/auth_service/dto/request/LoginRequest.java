package com.exe.skillverse_backend.auth_service.dto.request;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    /**
     * Optional remember-me hint from client.
     * - true: longer refresh session
     * - false/null: shorter refresh session
     */
    private Boolean rememberMe = false;
}
