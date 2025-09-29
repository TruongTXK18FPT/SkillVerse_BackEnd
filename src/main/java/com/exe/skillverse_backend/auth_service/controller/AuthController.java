package com.exe.skillverse_backend.auth_service.controller;

import com.exe.skillverse_backend.auth_service.dto.request.CompleteProfileRequest;
import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.request.RefreshTokenRequest;
import com.exe.skillverse_backend.auth_service.dto.request.ResendOtpRequest;
import com.exe.skillverse_backend.auth_service.dto.request.VerifyEmailRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.service.AuthService;
import com.exe.skillverse_backend.auth_service.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email and activate account", description = "Verifies email with OTP and activates account for login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified and account activated", content = @Content(schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid OTP or verification failed", content = @Content)
    })
    public ResponseEntity<RegistrationResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        try {
            RegistrationResponse response = authService.verifyEmailAndActivate(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Resends OTP for email verification")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP resent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid email or resend failed")
    })
    public ResponseEntity<String> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        try {
            emailVerificationService.resendOtp(request.getEmail());
            return ResponseEntity.ok("OTP resent successfully to " + request.getEmail());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Failed to resend OTP: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user credentials and returns JWT tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
            @ApiResponse(responseCode = "403", description = "Account pending approval", content = @Content)
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // refresh token khi user muon refresh token
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates a new access token using a valid refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token", content = @Content)
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Invalidates the current access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid token", content = @Content)
    })
    public ResponseEntity<Void> logout(
            @Parameter(description = "Authorization header with Bearer token", required = true) @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            authService.logout(token);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // verify token for testing
    @GetMapping("/verify")
    @Operation(summary = "Verify token", description = "Verifies if the provided token is valid and not expired")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token verification result", content = @Content(schema = @Schema(implementation = Boolean.class)))
    })
    public ResponseEntity<Boolean> verifyToken(
            @Parameter(description = "Authorization header with Bearer token", required = true) @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            boolean isValid = authService.verifyToken(token);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }
}
