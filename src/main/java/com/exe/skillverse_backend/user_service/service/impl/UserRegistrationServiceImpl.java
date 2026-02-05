package com.exe.skillverse_backend.user_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.user_service.dto.request.UserRegistrationRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserRegistrationResponse;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.user_service.service.UserRegistrationService;
import com.exe.skillverse_backend.parent_service.service.ParentService;
import com.exe.skillverse_backend.parent_service.dto.request.LinkStudentRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRegistrationServiceImpl
        implements UserRegistrationService {

    private final UserCreationService userCreationService;
    private final UserProfileRepository userProfileRepository;
    private final ParentService parentService;

    @Override
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        // Check if email already exists
        if (userCreationService.emailExists(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Note: Password validation is already handled by @PasswordMatches annotation on
        // BaseRegistrationRequest - no need to check manually here

        // Create User via auth service
        User user;
        if ("PARENT".equalsIgnoreCase(request.getRole())) {
            user = userCreationService.createUserForParent(request.getEmail(), request.getPassword(),
                    request.getFullName(), request.getPhone());
        } else {
            user = userCreationService.createUserForUser(request.getEmail(), request.getPassword(),
                    request.getFullName());
        }

        // Create user profile
        createUserProfile(user.getId(), request);
        // Note: FREE_TIER is already auto-assigned by UserCreationService during user creation
        // No need to call premiumService.assignFreeTierIfMissing() here - it would be redundant

        // Handle Parent-Child linking if applicable
        if ("PARENT".equalsIgnoreCase(request.getRole()) && request.getChildEmail() != null && !request.getChildEmail().isEmpty()) {
            try {
                LinkStudentRequest linkRequest = new LinkStudentRequest();
                linkRequest.setStudentEmail(request.getChildEmail());
                parentService.sendLinkRequest(user.getId(), linkRequest);
                log.info("Initiated link request to student: {}", request.getChildEmail());
            } catch (Exception e) {
                log.warn("Failed to link student {} during registration: {}", request.getChildEmail(), e.getMessage());
                // Don't fail registration, just log warning
            }
        }

        // Get OTP expiry time
        LocalDateTime otpExpiryTime = userCreationService.getOtpExpiryTime(request.getEmail());

        return UserRegistrationResponse.builder()
                .success(true)
                .email(request.getEmail())
                .userId(user.getId())
                .requiresVerification(true) // User registration always requires email verification
                .otpExpiryMinutes(5) // OTP expires in 5 minutes
                .otpExpiryTime(otpExpiryTime)
                .message("User registration successful! Please verify your email with the OTP code.")
                .nextStep("Check your email and verify with the OTP code to activate your account")
                .build();
    }

    private UserProfile createUserProfile(Long userId, UserRegistrationRequest request) {
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .fullName(request.getFullName())
                .bio(request.getBio())
                .phone(request.getPhone())
                .address(request.getAddress())
                .region(request.getRegion())
                .socialLinks(request.getSocialLinks())
                .build();

        return userProfileRepository.save(profile);
    }
}