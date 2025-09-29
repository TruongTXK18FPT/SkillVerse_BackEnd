package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorRegistrationRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorRegistrationResponse;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.shared.util.SecureAuditUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorRegistrationService
                implements RegistrationService<MentorRegistrationRequest, MentorRegistrationResponse> {

        private final UserCreationService userCreationService;
        private final MentorProfileRepository mentorProfileRepository;
        private final AuditService auditService;

        @Override
        @Transactional
        public MentorRegistrationResponse register(MentorRegistrationRequest request) {
                try {
                        log.info("Starting mentor registration for email: {}", request.getEmail());

                        // 1. Create User entity via auth_service
                        User user = userCreationService.createUserForMentor(
                                        request.getEmail(),
                                        request.getPassword(),
                                        request.getFullName());

                        // 2. Create MentorProfile in mentor_service
                        createMentorProfile(user, request);

                        // 3. Generate OTP for email verification (only after successful profile
                        // creation)
                        userCreationService.generateOtpForUser(request.getEmail());
                        log.info("Generated OTP for mentor user: {}", request.getEmail());

                        // 4. Log successful registration
                        String auditDetails = SecureAuditUtil.createRegistrationAuditDetails(request.getEmail(),
                                        "MENTOR");
                        auditService.logAction(user.getId(), "MENTOR_REGISTRATION", "MENTOR", user.getId().toString(),
                                        auditDetails);

                        return MentorRegistrationResponse.builder()
                                        .success(true)
                                        .message("Mentor registration successful! Your application is pending admin approval.")
                                        .email(request.getEmail())
                                        .userId(user.getId())
                                        .mentorProfileId(user.getId()) // MentorProfile uses userId as primary key
                                        .applicationStatus(ApplicationStatus.PENDING.name())
                                        .role("MENTOR")
                                        .requiresVerification(true)
                                        .otpExpiryMinutes(10)
                                        .nextStep("Check your email for verification code, then wait for admin approval")
                                        .build();

                } catch (Exception e) {
                        log.error("Mentor registration failed for email: {}", request.getEmail(), e);
                        auditService.logSystemAction("MENTOR_REGISTRATION_FAILED", "MENTOR", null,
                                        "Mentor registration failed for email: " + request.getEmail() + ", error: "
                                                        + e.getMessage());
                        throw e;
                }
        }

        /**
         * Register mentor with individual parameters (called from controller)
         */
        @Transactional
        public MentorRegistrationResponse registerMentor(
                        String email,
                        String password,
                        String confirmPassword,
                        String fullName,
                        String phone,
                        String bio,
                        String address,
                        String region,
                        String linkedinProfile,
                        String mainExpertiseArea,
                        Integer yearsOfExperience,
                        String personalProfile,
                        MultipartFile cvPortfolioFile,
                        MultipartFile certificatesFile) {

                try {
                        log.info("Starting mentor registration for email: {}", email);

                        // 1. Create and populate request object (business logic)
                        MentorRegistrationRequest request = createMentorRegistrationRequest(
                                        email, password, confirmPassword, fullName, phone, bio, address, region,
                                        linkedinProfile, mainExpertiseArea, yearsOfExperience, personalProfile,
                                        cvPortfolioFile, certificatesFile);

                        // 2. Process registration using existing logic
                        return register(request);

                } catch (Exception e) {
                        log.error("Mentor registration failed for email: {}", email, e);
                        throw e;
                }
        }

        /**
         * Create MentorRegistrationRequest from individual parameters (business logic)
         */
        private MentorRegistrationRequest createMentorRegistrationRequest(
                        String email,
                        String password,
                        String confirmPassword,
                        String fullName,
                        String phone,
                        String bio,
                        String address,
                        String region,
                        String linkedinProfile,
                        String mainExpertiseArea,
                        Integer yearsOfExperience,
                        String personalProfile,
                        MultipartFile cvPortfolioFile,
                        MultipartFile certificatesFile) {

                MentorRegistrationRequest request = new MentorRegistrationRequest();
                request.setEmail(email);
                request.setPassword(password);
                request.setConfirmPassword(confirmPassword);
                request.setFullName(fullName);
                request.setPhone(phone);
                request.setBio(bio);
                request.setAddress(address);
                request.setRegion(region);
                request.setLinkedinProfile(linkedinProfile);
                request.setMainExpertiseArea(mainExpertiseArea);
                request.setYearsOfExperience(yearsOfExperience);
                request.setPersonalProfile(personalProfile);

                // Handle file uploads (business logic)
                if (cvPortfolioFile != null && !cvPortfolioFile.isEmpty()) {
                        String cvUrl = "uploads/mentor/cv/" + email + "_" + cvPortfolioFile.getOriginalFilename();
                        request.setCvPortfolioUrl(cvUrl);
                        log.info("CV file received: {} (size: {} bytes)", cvPortfolioFile.getOriginalFilename(),
                                        cvPortfolioFile.getSize());
                }

                if (certificatesFile != null && !certificatesFile.isEmpty()) {
                        String certUrl = "uploads/mentor/certificates/" + email + "_"
                                        + certificatesFile.getOriginalFilename();
                        request.setCertificatesUrl(certUrl);
                        log.info("Certificates file received: {} (size: {} bytes)",
                                        certificatesFile.getOriginalFilename(),
                                        certificatesFile.getSize());
                }

                return request;
        }

        /**
         * Create MentorProfile with new form fields and application pending status
         */
        private void createMentorProfile(User user, MentorRegistrationRequest request) {
                MentorProfile mentorProfile = MentorProfile.builder()
                                .user(user) // Set the User entity reference for @MapsId (userId will be auto-derived)
                                // New form fields
                                .fullName(request.getFullName())
                                .email(request.getEmail())
                                .linkedinProfile(request.getLinkedinProfile())
                                .mainExpertiseAreas(request.getMainExpertiseArea())
                                .yearsOfExperience(request.getYearsOfExperience())
                                .personalProfile(request.getPersonalProfile())
                                .cvPortfolioUrl(request.getCvPortfolioUrl())
                                .certificatesUrl(request.getCertificatesUrl())
                                // Application status
                                .applicationStatus(ApplicationStatus.PENDING)
                                .applicationDate(LocalDateTime.now())
                                .build();

                mentorProfileRepository.save(mentorProfile);
                log.info("Created mentor profile for user: {} with full name: {}", user.getId(), request.getFullName());
        }
}