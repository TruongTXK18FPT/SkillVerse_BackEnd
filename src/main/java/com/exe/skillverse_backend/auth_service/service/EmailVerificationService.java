package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 3;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    /**
     * Generate and store OTP for email verification
     */
    @Transactional
    public String generateOtpForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Rate limiting: Check time since last OTP sent
        if (user.getLastOtpSentTime() != null) {
            long secondsSinceLastOtp = Duration.between(
                    user.getLastOtpSentTime(),
                    LocalDateTime.now()).getSeconds();

            if (secondsSinceLastOtp < RESEND_COOLDOWN_SECONDS) {
                long remainingSeconds = RESEND_COOLDOWN_SECONDS - secondsSinceLastOtp;
                throw new RuntimeException(
                        "Please wait " + remainingSeconds + " seconds before requesting a new OTP");
            }
        }

        // Check if user is already verified
        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }

        // Generate 6-digit OTP
        String otp = generateRandomOtp();

        // Set OTP and expiry time
        user.setVerificationOtp(otp);
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        user.setOtpAttempts(0);
        user.setLastOtpSentTime(LocalDateTime.now()); // Track last OTP sent time

        userRepository.save(user);

        // Send OTP via email
        emailService.sendOtpEmail(email, otp);

        // log.info("Generated OTP for user: {} (expires in {} minutes)", email,
        // OTP_EXPIRY_MINUTES);
        return otp;
    }

    /**
     * Generate OTP for password reset (uses different email template)
     */
    public String generateOtpForPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Rate limiting: Check if user requested OTP too recently
        if (user.getLastOtpSentTime() != null) {
            long secondsSinceLastOtp = Duration.between(user.getLastOtpSentTime(), LocalDateTime.now())
                    .getSeconds();
            if (secondsSinceLastOtp < RESEND_COOLDOWN_SECONDS) {
                long remainingSeconds = RESEND_COOLDOWN_SECONDS - secondsSinceLastOtp;
                throw new RuntimeException(
                        "Please wait " + remainingSeconds + " seconds before requesting a new OTP");
            }
        }

        // Generate 6-digit OTP
        String otp = generateRandomOtp();

        // Set OTP and expiry time
        user.setVerificationOtp(otp);
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        user.setOtpAttempts(0);
        user.setLastOtpSentTime(LocalDateTime.now());

        userRepository.save(user);

        // Send password reset OTP via email (different template)
        emailService.sendPasswordResetOtpEmail(email, otp);

        // log.info("Generated password reset OTP for user: {} (expires in {} minutes)",
        // email, OTP_EXPIRY_MINUTES);
        return otp;
    }

    /**
     * Verify OTP for email verification (does not activate account)
     * Account activation is handled by the calling service based on user role
     */
    @Transactional
    public boolean verifyOtp(String email, String providedOtp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Check if user is already verified
        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }

        // Check if OTP exists
        if (user.getVerificationOtp() == null) {
            throw new RuntimeException("No OTP found. Please request a new OTP");
        }

        // Check if OTP has expired
        if (user.getOtpExpiryTime().isBefore(LocalDateTime.now())) {
            // Clear expired OTP
            user.setVerificationOtp(null);
            user.setOtpExpiryTime(null);
            userRepository.save(user);
            throw new RuntimeException("OTP has expired. Please request a new OTP");
        }

        // Check attempt limit
        if (user.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            // Clear OTP after max attempts
            user.setVerificationOtp(null);
            user.setOtpExpiryTime(null);
            user.setOtpAttempts(0);
            userRepository.save(user);
            throw new RuntimeException("Maximum OTP attempts exceeded. Please request a new OTP");
        }

        // Increment attempt count
        user.setOtpAttempts(user.getOtpAttempts() + 1);

        // Verify OTP
        if (!user.getVerificationOtp().equals(providedOtp)) {
            userRepository.save(user);
            log.warn("Invalid OTP attempt for user: {} (attempt {}/{})", email, user.getOtpAttempts(),
                    MAX_OTP_ATTEMPTS);
            throw new RuntimeException(
                    "Invalid OTP. Attempts remaining: " + (MAX_OTP_ATTEMPTS - user.getOtpAttempts()));
        }

        // OTP is valid - verify user email only (status handled by calling service
        // based on role)
        user.setEmailVerified(true);
        // Note: User status activation is handled by the calling service based on role
        // Regular users can be activated immediately, but mentors/recruiters need admin
        // approval
        user.setVerificationOtp(null);
        user.setOtpExpiryTime(null);
        user.setOtpAttempts(0);
        userRepository.save(user);

        log.info("Email successfully verified for: {}", email);
        return true;
    }

    /**
     * Check if user's email is verified
     */
    public boolean isEmailVerified(String email) {
        return userRepository.findByEmail(email)
                .map(User::isEmailVerified)
                .orElse(false);
    }

    /**
     * Resend OTP (generate new one)
     */
    @Transactional
    public String resendOtp(String email) {
        log.info("Resending OTP for user: {}", email);
        return generateOtpForUser(email);
    }

    /**
     * Generate random 6-digit OTP
     */
    private String generateRandomOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // Generates 6-digit number
        return String.valueOf(otp);
    }

    /**
     * Get OTP expiry time for a user
     */
    public LocalDateTime getOtpExpiryTime(String email) {
        return userRepository.findByEmail(email)
                .map(User::getOtpExpiryTime)
                .orElse(null);
    }

    /**
     * Get remaining OTP attempts for a user
     */
    public int getRemainingOtpAttempts(String email) {
        return userRepository.findByEmail(email)
                .map(user -> MAX_OTP_ATTEMPTS - user.getOtpAttempts())
                .orElse(0);
    }
}