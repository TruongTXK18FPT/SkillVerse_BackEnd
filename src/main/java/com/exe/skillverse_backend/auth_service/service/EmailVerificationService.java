package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 3;

    /**
     * Generate and store OTP for email verification
     */
    @Transactional
    public String generateOtpForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Check if user is already verified
        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }

        // Generate 6-digit OTP
        String otp = generateRandomOtp();

        // Set OTP and expiry time
        user.setVerificationOtp(otp);
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        user.setOtpAttempts(0); // Reset attempts when new OTP is generated

        userRepository.save(user);

        // Send OTP via email
        emailService.sendOtpEmail(email, otp);

        log.info("Generated OTP for user: {} (expires in {} minutes)", email, OTP_EXPIRY_MINUTES);
        return otp;
    }

    /**
     * Verify OTP and activate user account
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

        // OTP is valid - verify user
        user.setEmailVerified(true);
        user.setVerificationOtp(null);
        user.setOtpExpiryTime(null);
        user.setOtpAttempts(0);
        userRepository.save(user);

        log.info("Email successfully verified for user: {}", email);
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