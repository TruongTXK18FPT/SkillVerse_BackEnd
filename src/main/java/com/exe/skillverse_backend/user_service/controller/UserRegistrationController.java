package com.exe.skillverse_backend.user_service.controller;

import com.exe.skillverse_backend.user_service.dto.request.UserRegistrationRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserRegistrationResponse;
import com.exe.skillverse_backend.user_service.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Registration", description = "User registration and management endpoints")
public class UserRegistrationController {

    private final UserRegistrationService userRegistrationService;

    @PostMapping("/register")
    @Operation(summary = "Register as User", description = "Register a new user account with complete profile creation")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully", content = @Content(schema = @Schema(implementation = UserRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid registration data"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<UserRegistrationResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        log.info("User registration attempt for email: {}", request.getEmail());

        try {
            UserRegistrationResponse response = userRegistrationService.register(request);
            log.info("User registration successful for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("User registration failed for email: {}", request.getEmail(), e);
            throw e;
        }
    }
}