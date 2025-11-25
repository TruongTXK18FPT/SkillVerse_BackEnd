package com.exe.skillverse_backend.user_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.user_service.dto.request.UserRegistrationRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserRegistrationResponse;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRegistrationService implements RegistrationService<UserRegistrationRequest, UserRegistrationResponse> {

    private final UserCreationService userCreationService;
    private final UserProfileRepository userProfileRepository;
    private final PremiumService premiumService;

    @Override
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        // Check if email already exists
        if (userCreationService.emailExists(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Password validation is handled by @PasswordMatches annotation on
        // BaseRegistrationRequest
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password and confirmation password do not match");
        }

        // Create User via auth service
        User user = userCreationService.createUserForUser(request.getEmail(), request.getPassword(),
                request.getFullName());

        // Create user profile
        createUserProfile(user.getId(), request);
        // Assign Free Tier by default
        premiumService.assignFreeTierIfMissing(user.getId());

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