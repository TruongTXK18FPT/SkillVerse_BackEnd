package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.business_service.service.BusinessRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Business Registration", description = "Business/Recruiter registration and management endpoints")
@Validated
public class BusinessRegistrationController {

    private final BusinessRegistrationService businessRegistrationService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam("companyName") String companyName,
            @RequestParam("companyWebsite") String companyWebsite,
            @RequestParam("companyAddress") String companyAddress,
            @RequestParam("taxCodeOrBusinessRegistrationNumber") String taxCodeOrBusinessRegistrationNumber,
            @RequestParam(value = "contactPersonPhone", required = false) String contactPersonPhone,
            @RequestParam("contactPersonPosition") String contactPersonPosition,
            @RequestParam("companySize") String companySize,
            @RequestParam("industry") String industry,
            @RequestParam(value = "companyDocumentsFile", required = false) MultipartFile companyDocumentsFile,
            @RequestParam(value = "companyDocumentsFiles", required = false) java.util.List<MultipartFile> companyDocumentsFiles) {
        try {
            log.info("Processing business registration for email: {}", email);

            // Delegate all business logic to service layer
            BusinessRegistrationResponse response = businessRegistrationService.registerBusiness(
                    email, password, confirmPassword, fullName, phone, bio, address, region,
                    companyName, companyWebsite, companyAddress, taxCodeOrBusinessRegistrationNumber,
                    contactPersonPhone, contactPersonPosition, companySize, industry,
                    companyDocumentsFile,
                    companyDocumentsFiles);

            log.info("Business registration successful for email: {}", email);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Business registration failed - bad request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Business registration failed for email: {}", email, e);
            throw e;
        }
    }
}
