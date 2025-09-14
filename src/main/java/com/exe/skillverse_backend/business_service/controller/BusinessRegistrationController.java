package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.BusinessRegistrationRequest;
import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.business_service.service.BusinessRegistrationService;
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
@RequestMapping("/api/business")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Business Registration", description = "Business/Recruiter registration and management endpoints")
public class BusinessRegistrationController {

    private final BusinessRegistrationService businessRegistrationService;

    @PostMapping("/register")
    @Operation(summary = "Register as a business/recruiter", description = "Register a new business/recruiter account with company information. "
            +
            "Creates User entity in auth service and RecruiterProfile in business service. " +
            "Application will be pending admin approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Business registration successful", content = @Content(schema = @Schema(implementation = BusinessRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data or email already exists", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<BusinessRegistrationResponse> registerBusiness(
            @Valid @RequestBody BusinessRegistrationRequest request) {
        try {
            log.info("Processing business registration for email: {}", request.getEmail());

            BusinessRegistrationResponse response = businessRegistrationService.register(request);

            log.info("Business registration successful for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Business registration failed - bad request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Business registration failed for email: {}", request.getEmail(), e);
            throw e;
        }
    }
}