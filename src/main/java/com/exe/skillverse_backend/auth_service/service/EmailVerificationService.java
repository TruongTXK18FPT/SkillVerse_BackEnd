package com.exe.skillverse_backend.auth_service.service;

import java.time.LocalDateTime;

public interface EmailVerificationService {

    String generateOtpForUser(String email);

    String generateOtpForPasswordReset(String email);

    boolean verifyOtp(String email, String providedOtp);

    boolean isEmailVerified(String email);

    String resendOtp(String email);

    LocalDateTime getOtpExpiryTime(String email);

    int getRemainingOtpAttempts(String email);
}
