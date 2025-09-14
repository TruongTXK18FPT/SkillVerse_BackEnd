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
public class MentorRegistrationService
        implements RegistrationService<MentorRegistrationRequest, MentorRegistrationResponse> {

    private final UserCreationService userCreationService;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserProfileService userProfileService;
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

            // 2. Create UserProfile in user_service
            createUserProfile(user.getId(), request);

            // 3. Create MentorProfile in mentor_service
            createMentorProfile(user.getId(), request);

            // 4. Log successful registration
            auditService.logAction(user.getId(), "MENTOR_REGISTRATION", "MENTOR", user.getId().toString(),
                    "Mentor registered successfully: " + request.getEmail() + " - awaiting admin approval");

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
                    "Mentor registration failed for email: " + request.getEmail() + ", error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Create UserProfile for the mentor using CreateProfileRequest
     */
    private void createUserProfile(Long userId, MentorRegistrationRequest request) {
        CreateProfileRequest profileRequest = new CreateProfileRequest();
        profileRequest.setFullName(request.getFullName());
        profileRequest.setBio(request.getBio());
        profileRequest.setPhone(request.getPhone());
        profileRequest.setAddress(request.getAddress());
        profileRequest.setRegion(request.getRegion());

        userProfileService.createProfile(userId, profileRequest);
    }

    /**
     * Create MentorProfile with application pending status
     */
    private void createMentorProfile(Long userId, MentorRegistrationRequest request) {
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(userId)
                .expertiseAreas(request.getExpertise()) // Changed from expertise to expertiseAreas
                .bio(request.getMotivation()) // Store motivation in bio field
                .yearsOfExperience(extractYearsFromExperience(request.getTeachingExperience()))
                .hourlyRate(request.getHourlyRate())
                .linkedinUrl(request.getLinkedinUrl())
                .portfolioUrl(request.getGithubUrl()) // Store github in portfolio field
                .applicationStatus(ApplicationStatus.PENDING)
                .applicationDate(LocalDateTime.now())
                .build();

        mentorProfileRepository.save(mentorProfile);
        log.info("Created mentor profile for user: {}", userId);
    }

    /**
     * Extract years of experience from teaching experience text
     */
    private Integer extractYearsFromExperience(String teachingExperience) {
        if (teachingExperience == null)
            return null;

        // Try to extract number from the text (simple regex approach)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*year");
        java.util.regex.Matcher matcher = pattern.matcher(teachingExperience.toLowerCase());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}