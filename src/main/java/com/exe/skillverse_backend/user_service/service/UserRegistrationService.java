package com.exe.skillverse_backend.user_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.shared.service.RegistrationService;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.shared.util.SecureAuditUtil;
import com.exe.skillverse_backend.user_service.dto.request.UserRegistrationRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserRegistrationResponse;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRegistrationService implements RegistrationService<UserRegistrationRequest, UserRegistrationResponse> {

    private final UserCreationService userCreationService;
    private final UserProfileRepository userProfileRepository;
    private final AuditService auditService;

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
        UserProfile profile = createUserProfile(user.getId(), request);

        // Secure audit log - NO PII/sensitive data
        String secureAuditDetails = SecureAuditUtil.createRegistrationAuditDetails(
                request.getEmail(),
                "USER");
        auditService.logAction(user.getId(), "USER_REGISTRATION", "USER", user.getId().toString(),
                secureAuditDetails);

        return UserRegistrationResponse.builder()
                .success(true)
                .email(request.getEmail())
                .userId(user.getId())
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