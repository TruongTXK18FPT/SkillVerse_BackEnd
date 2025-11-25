package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.dto.request.ChangePasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.request.SetPasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.response.ForgotPasswordResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.entity.AuthProvider;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Initiate forgot password process - send OTP to user's email
     * Reuses EmailVerificationService for OTP generation and sending
     * 
     * @param email User's email address
     * @return ForgotPasswordResponse with success status and instructions
     * @throws RuntimeException if user not found or account not active
     */
    @Transactional
    public ForgotPasswordResponse initiateForgotPassword(String email) {
        log.info("Initiating forgot password for email: {}", email);

        // Check if user exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Check if user account is active
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Account is not active. Please contact support.");
        }

        // Check if email is verified
        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email is not verified. Please verify your email first.");
        }

        // ✅ Generate and send password reset OTP (uses different email template)
        emailVerificationService.generateOtpForPasswordReset(email);

        // Get OTP expiry time from user
        LocalDateTime otpExpiryTime = emailVerificationService.getOtpExpiryTime(email);

        log.info("Forgot password OTP sent successfully to: {}", email);

        return ForgotPasswordResponse.builder()
                .success(true)
                .message("Password reset OTP has been sent to your email")
                .email(email)
                .otpExpiryMinutes(5)
                .otpExpiryTime(otpExpiryTime)
                .nextStep("Check your email and enter the OTP code to reset your password")
                .build();
    }

    /**
     * Reset user password after OTP verification
     * 
     * @param request ResetPasswordRequest containing email, OTP, and new password
     * @return RegistrationResponse with success message
     * @throws RuntimeException if validation fails or OTP is invalid
     */
    @Transactional
    public RegistrationResponse resetPassword(ResetPasswordRequest request) {
        log.info("Attempting password reset for email: {}", request.getEmail());

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        // Get user first to temporarily set as unverified for OTP verification
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean wasVerified = user.isEmailVerified();
        user.setEmailVerified(false);
        userRepository.save(user);

        try {
            // ✅ REUSE: Verify OTP using existing EmailVerificationService
            emailVerificationService.verifyOtp(request.getEmail(), request.getOtp());
        } catch (RuntimeException e) {
            // Restore verification status on error
            user.setEmailVerified(wasVerified);
            userRepository.save(user);
            throw e;
        }

        // Restore verification status after successful OTP verification
        user.setEmailVerified(wasVerified);

        // Encode and set new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Clear OTP data
        user.setVerificationOtp(null);
        user.setOtpExpiryTime(null);
        user.setOtpAttempts(0);

        userRepository.save(user);

        log.info("Password reset successful for user: {}", request.getEmail());

        return RegistrationResponse.builder()
                .message("Password reset successful! You can now login with your new password.")
                .email(request.getEmail())
                .requiresVerification(false)
                .nextStep("Use /api/auth/login to login with your new password")
                .build();
    }

    /**
     * Set password for Google OAuth users who don't have a password yet
     * This allows them to login using email+password as a backup method
     * 
     * @param userId  User ID from JWT token (authenticated user)
     * @param request SetPasswordRequest containing new password
     * @return RegistrationResponse with success message
     * @throws RuntimeException if user not found, already has password, or not a
     *                          Google user
     */
    @Transactional
    public RegistrationResponse setPasswordForGoogleUser(Long userId, SetPasswordRequest request) {
        log.info("Setting password for Google user with ID: {}", userId);

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate user is from Google OAuth
        if (user.getAuthProvider() != AuthProvider.GOOGLE) {
            throw new RuntimeException("This feature is only available for Google OAuth users");
        }

        // Check if user already has a password
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            throw new RuntimeException("User already has a password set. Use 'Change Password' instead.");
        }

        // Encode and set password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Update auth provider to allow dual authentication
        // User can now login with both Google AND email+password
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setGoogleLinked(true); // Keep Google login available

        userRepository.save(user);

        log.info("Password set successfully for Google user: {}", user.getEmail());

        return RegistrationResponse.builder()
                .message("Password set successfully! You can now login with both Google and email+password.")
                .email(user.getEmail())
                .requiresVerification(false)
                .nextStep("You now have dual authentication: Google OAuth and email+password")
                .build();
    }

    /**
     * Change password for authenticated users
     * Requires current password verification for security
     * 
     * @param userId  User ID from JWT token (authenticated user)
     * @param request ChangePasswordRequest containing current and new password
     * @return RegistrationResponse with success message
     * @throws RuntimeException if user not found, current password incorrect, or
     *                          validation fails
     */
    @Transactional
    public RegistrationResponse changePassword(Long userId, ChangePasswordRequest request) {
        log.info("Changing password for user with ID: {}", userId);

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user has a password (Google users without password cannot use this)
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new RuntimeException("You don't have a password set. Please use 'Set Password' feature first.");
        }

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Check if new password is same as current password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }

        // Encode and update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);

        log.info("Password changed successfully for user: {}", user.getEmail());

        return RegistrationResponse.builder()
                .message("Password changed successfully!")
                .email(user.getEmail())
                .requiresVerification(false)
                .nextStep("Your password has been updated. Please use the new password for future logins.")
                .build();
    }
}
