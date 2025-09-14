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
import com.exe.skillverse_backend.user_service.dto.request.CreateProfileRequest;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
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
    private final UserProfileService userProfileService;
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

            // 2. Create UserProfile in user_service
            createUserProfile(user.getId(), request);

            // 3. Create RecruiterProfile in business_service
            createRecruiterProfile(user.getId(), request);

            // 4. Log successful registration
            auditService.logAction(user.getId(), "RECRUITER_REGISTRATION", "RECRUITER", user.getId().toString(),
                    "Recruiter registered successfully: " + request.getEmail() + " - awaiting admin approval");

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
     * Create UserProfile for the recruiter using CreateProfileRequest
     */
    private void createUserProfile(Long userId, BusinessRegistrationRequest request) {
        CreateProfileRequest profileRequest = new CreateProfileRequest();
        profileRequest.setFullName(request.getFullName());
        profileRequest.setBio(request.getBio());
        profileRequest.setPhone(request.getPhone());
        profileRequest.setAddress(request.getAddress());
        profileRequest.setRegion(request.getRegion());

        userProfileService.createProfile(userId, profileRequest);
    }

    /**
     * Create RecruiterProfile with application pending status
     */
    private void createRecruiterProfile(Long userId, BusinessRegistrationRequest request) {
        RecruiterProfile recruiterProfile = RecruiterProfile.builder()
                .userId(userId)
                .companyName(request.getCompanyName())
                .companyDescription(request.getCompanyDescription())
                .companyWebsite(request.getCompanyWebsite())
                .industry(request.getIndustry())
                .companySize(request.getCompanySize())
                .jobTitle(request.getJobTitle())
                .workEmail(request.getEmail()) // Store work email same as registration email
                .phoneNumber(request.getPhone())
                .companyAddress(request.getAddress())
                .applicationStatus(ApplicationStatus.PENDING)
                .applicationDate(LocalDateTime.now())
                .build();

        recruiterProfileRepository.save(recruiterProfile);
        log.info("Created recruiter profile for user: {}", userId);
    }
}