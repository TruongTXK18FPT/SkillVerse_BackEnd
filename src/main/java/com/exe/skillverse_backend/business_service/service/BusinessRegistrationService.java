package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.business_service.dto.request.BusinessRegistrationRequest;
import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.shared.util.SecureAuditUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessRegistrationService
        implements RegistrationService<BusinessRegistrationRequest, BusinessRegistrationResponse> {

    private final UserCreationService userCreationService;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public BusinessRegistrationResponse register(BusinessRegistrationRequest request) {
        try {
            log.info("Starting business/recruiter registration for email: {}", request.getEmail());

            // 1. Create User entity via auth_service
            User user = userCreationService.createUserForRecruiter(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName());

            // 2. Create RecruiterProfile in business_service
            createRecruiterProfile(user, request);

            // 3. Generate OTP for email verification (only after successful profile
            // creation)
            userCreationService.generateOtpForUser(request.getEmail());
            log.info("Generated OTP for recruiter user: {}", request.getEmail());

            // 4. Log successful registration
            String auditDetails = SecureAuditUtil.createRegistrationAuditDetails(request.getEmail(), "RECRUITER");
            auditService.logAction(user.getId(), "RECRUITER_REGISTRATION", "RECRUITER", user.getId().toString(),
                    auditDetails);

            return BusinessRegistrationResponse.builder()
                    .success(true)
                    .message("Business registration successful! Your application is pending admin approval.")
                    .email(request.getEmail())
                    .userId(user.getId())
                    .recruiterProfileId(user.getId()) // RecruiterProfile uses userId as primary key
                    .applicationStatus(ApplicationStatus.PENDING.name())
                    .role("RECRUITER")
                    .companyName(request.getCompanyName())
                    .requiresVerification(true)
                    .otpExpiryMinutes(10)
                    .nextStep("Check your email for verification code, then wait for admin approval")
                    .build();

        } catch (Exception e) {
            log.error("Business registration failed for email: {}", request.getEmail(), e);
            auditService.logSystemAction("RECRUITER_REGISTRATION_FAILED", "RECRUITER", null,
                    "Recruiter registration failed for email: " + request.getEmail() + ", error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Create RecruiterProfile with application pending status
     */
    private void createRecruiterProfile(User user, BusinessRegistrationRequest request) {
        RecruiterProfile recruiterProfile = RecruiterProfile.builder()
                .user(user) // Set the User entity reference for @MapsId (userId will be auto-derived)
                .companyName(request.getCompanyName())
                .companyWebsite(request.getCompanyWebsite())
                .companyAddress(request.getCompanyAddress())
                .taxCodeOrBusinessRegistrationNumber(request.getTaxCodeOrBusinessRegistrationNumber())
                .companyDocumentsUrl(request.getCompanyDocumentsUrl())
                .applicationStatus(ApplicationStatus.PENDING)
                .applicationDate(LocalDateTime.now())
                .build();

        recruiterProfileRepository.save(recruiterProfile);
        log.info("Created recruiter profile for user: {} with company: {}", user.getId(), request.getCompanyName());
    }
}