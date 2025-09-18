package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.*;
import com.exe.skillverse_backend.auth_service.repository.*;
import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.shared.exception.AccountPendingApprovalException;
import com.exe.skillverse_backend.shared.exception.AuthenticationException;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.shared.util.SecureAuditUtil;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final RefreshTokenRepository refreshTokenRepository;
        private final InvalidatedTokenRepository invalidatedTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final AuditService auditService;
        private final UserProfileService userProfileService;
        private final MentorProfileRepository mentorProfileRepository;
        private final RecruiterProfileRepository recruiterProfileRepository;
        private final EmailVerificationService emailVerificationService;
        private final EmailService emailService;

        @Value("${jwt.secret:mySecretKey}")
        private String jwtSecret;

        @Value("${jwt.access-token-expiration:3600}") // 1 hour
        private Long accessTokenExpiration;

        @Value("${jwt.refresh-token-expiration:86400}") // 24 hours
        private Long refreshTokenExpiration;

        @Transactional
        public RegistrationResponse verifyEmailAndActivate(String email, String otp) {
                try {
                        log.info("Verifying email and activating user: {}", email);

                        // Verify the OTP
                        emailVerificationService.verifyOtp(email, otp);

                        // Find the user and activate account
                        User user = userRepository.findByEmail(email)
                                        .orElseThrow(() -> new RuntimeException("User not found"));

                        // Only activate regular users - mentors/recruiters need admin approval
                        if (user.getPrimaryRole() == PrimaryRole.USER) {
                                user.setStatus(UserStatus.ACTIVE);
                                log.info("Activated regular user account: {}", email);

                                // Send welcome email only for regular users
                                emailService.sendWelcomeEmail(email, null);
                        } else {
                                // For mentors/recruiters, just mark email as verified but keep INACTIVE
                                log.info("Email verified for {}, but keeping INACTIVE pending admin approval: {}",
                                                user.getPrimaryRole(), email);
                                // No welcome email - they will get approval/rejection email later from admin
                        }
                        user = userRepository.save(user);

                        // Log successful verification
                        String actionType = user.getPrimaryRole() == PrimaryRole.USER ? "EMAIL_VERIFIED_ACTIVATED"
                                        : "EMAIL_VERIFIED_PENDING_APPROVAL";
                        auditService.logAction(user.getId(), actionType, "USER", user.getId().toString(),
                                        "Email verified for " + user.getPrimaryRole() + ": " + email);

                        String message = user.getPrimaryRole() == PrimaryRole.USER
                                        ? "Email verified successfully! You can now login with your credentials."
                                        : "Email verified successfully! Your "
                                                        + user.getPrimaryRole().toString().toLowerCase()
                                                        + " application is pending admin approval.";

                        String nextStep = user.getPrimaryRole() == PrimaryRole.USER
                                        ? "Use /api/auth/login to get your access tokens"
                                        : "Please wait for admin approval before logging in";

                        return RegistrationResponse.builder()
                                        .message(message)
                                        .email(email)
                                        .requiresVerification(false)
                                        .otpExpiryMinutes(0)
                                        .nextStep(nextStep)
                                        .build();

                } catch (Exception e) {
                        auditService.logSystemAction("EMAIL_VERIFICATION_FAILED", "USER", null,
                                        "Email verification failed for: " + email + ", error: " + e.getMessage());
                        throw e;
                }
        }

        @Transactional
        public AuthResponse login(LoginRequest request) {
                try {
                        // Find user by email
                        User user = userRepository.findByEmail(request.getEmail())
                                        .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

                        // Check user status - only ACTIVE users can login
                        if (user.getStatus() != UserStatus.ACTIVE) {
                                String auditDetails = SecureAuditUtil.createAuthAuditDetails(request.getEmail(),
                                                "LOGIN_FAILED_INACTIVE", false, null);
                                auditService.logAction(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(),
                                                auditDetails);

                                // Provide specific error messages based on user type
                                if (!user.isEmailVerified()) {
                                        throw new AuthenticationException(
                                                        "Email not verified. Please verify your email before logging in.");
                                } else if (user.getPrimaryRole() == PrimaryRole.MENTOR) {
                                        throw AccountPendingApprovalException.forMentor();
                                } else if (user.getPrimaryRole() == PrimaryRole.RECRUITER) {
                                        throw AccountPendingApprovalException.forRecruiter();
                                } else {
                                        throw new AuthenticationException(
                                                        "Your account is inactive. Please contact admin support for assistance.");
                                }
                        }

                        // Verify password
                        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                                String auditDetails = SecureAuditUtil.createAuthAuditDetails(request.getEmail(),
                                                "LOGIN_FAILED_INVALID_PASSWORD", false, null);
                                auditService.logAction(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(),
                                                auditDetails);
                                throw new AuthenticationException("Invalid credentials");
                        }

                        // Generate tokens
                        String accessToken = generateToken(user);
                        String refreshToken = generateRefreshToken(user);

                        // Log successful login
                        String auditDetails = SecureAuditUtil.createAuthAuditDetails(request.getEmail(),
                                        "LOGIN_SUCCESS", true, null);
                        auditService.logAction(user.getId(), "LOGIN", "USER", user.getId().toString(),
                                        auditDetails);

                        // Get user profile information if exists
                        String fullName = getUserFullName(user);

                        // Build user DTO
                        UserDto userDto = new UserDto();
                        userDto.setId(user.getId());
                        userDto.setEmail(user.getEmail());
                        userDto.setFullName(fullName);
                        userDto.setRoles(user.getRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(Collectors.toSet()));

                        return AuthResponse.builder()
                                        .accessToken(accessToken)
                                        .refreshToken(refreshToken)
                                        .tokenType("Bearer")
                                        .expiresIn(accessTokenExpiration)
                                        .user(userDto)
                                        .build();

                } catch (Exception e) {
                        auditService.logSystemAction("LOGIN_FAILED", "USER", null,
                                        "Login failed for email: " + request.getEmail() + ", error: " + e.getMessage());
                        throw e;
                }
        }

        public String generateToken(User user) {
                try {
                        Date expirationDate = new Date(System.currentTimeMillis() + accessTokenExpiration * 1000);
                        String jti = UUID.randomUUID().toString();

                        // Create roles list for JWT claims
                        List<String> roles = user.getRoles().stream()
                                        .map(Role::getName)
                                        .collect(Collectors.toList());

                        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                                        .subject(user.getId().toString())
                                        .issuer("skillverse")
                                        .issueTime(new Date())
                                        .expirationTime(expirationDate)
                                        .jwtID(jti)
                                        .claim("userId", user.getId()) // Explicit user ID claim
                                        .claim("email", user.getEmail())
                                        .claim("scope", buildScope(user))
                                        .claim("roles", roles) // Add roles claim for @PreAuthorize
                                        .build();

                        SignedJWT signedJWT = new SignedJWT(
                                        new JWSHeader(JWSAlgorithm.HS512),
                                        claimsSet);

                        JWSSigner signer = new MACSigner(jwtSecret.getBytes());
                        signedJWT.sign(signer);

                        return signedJWT.serialize();

                } catch (Exception e) {
                        log.error("Error generating JWT token", e);
                        throw new RuntimeException("Error generating token");
                }
        }

        public boolean verifyToken(String token) {
                try {
                        SignedJWT signedJWT = SignedJWT.parse(token);

                        // Verify signature
                        JWSVerifier verifier = new MACVerifier(jwtSecret.getBytes());
                        if (!signedJWT.verify(verifier)) {
                                return false;
                        }

                        // Check expiration
                        Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
                        if (expirationTime.before(new Date())) {
                                return false;
                        }

                        // Check if token is invalidated
                        String jti = signedJWT.getJWTClaimsSet().getJWTID();
                        if (invalidatedTokenRepository.existsByJti(jti)) {
                                return false;
                        }

                        return true;

                } catch (Exception e) {
                        log.error("Error verifying JWT token", e);
                        return false;
                }
        }

        @Transactional
        public AuthResponse refreshToken(String refreshToken) {
                try {
                        RefreshToken storedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                                        .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

                        // Check expiration
                        if (storedRefreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                                refreshTokenRepository.delete(storedRefreshToken);
                                throw new RuntimeException("Refresh token expired");
                        }

                        // Get user
                        User user = userRepository.findById(storedRefreshToken.getUserId())
                                        .orElseThrow(() -> new RuntimeException("User not found"));

                        // Generate new tokens
                        String newAccessToken = generateToken(user);
                        String newRefreshToken = generateRefreshToken(user);

                        // Delete old refresh token
                        refreshTokenRepository.delete(storedRefreshToken);

                        // Log action
                        auditService.logAction(user.getId(), "TOKEN_REFRESH", "USER", user.getId().toString(),
                                        "Tokens refreshed for user: " + user.getEmail());

                        return AuthResponse.builder()
                                        .accessToken(newAccessToken)
                                        .refreshToken(newRefreshToken)
                                        .tokenType("Bearer")
                                        .expiresIn(accessTokenExpiration)
                                        .build();

                } catch (Exception e) {
                        auditService.logSystemAction("TOKEN_REFRESH_FAILED", "USER", null,
                                        "Token refresh failed: " + e.getMessage());
                        throw e;
                }
        }

        @Transactional
        public void logout(String accessToken) {
                try {
                        SignedJWT signedJWT = SignedJWT.parse(accessToken);
                        String jti = signedJWT.getJWTClaimsSet().getJWTID();
                        String userId = signedJWT.getJWTClaimsSet().getSubject();

                        // Invalidate access token
                        InvalidatedToken invalidatedToken = new InvalidatedToken(jti);
                        invalidatedTokenRepository.save(invalidatedToken);

                        // Delete refresh token
                        refreshTokenRepository.deleteByUserId(Long.parseLong(userId));

                        // Log action
                        auditService.logAction(Long.parseLong(userId), "LOGOUT", "USER", userId,
                                        "User logged out successfully");

                } catch (Exception e) {
                        log.error("Error during logout", e);
                        auditService.logSystemAction("LOGOUT_FAILED", "USER", null,
                                        "Logout failed: " + e.getMessage());
                        throw new RuntimeException("Error during logout");
                }
        }

        public String buildScope(User user) {
                return user.getRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.joining(" "));
        }

        private String generateRefreshToken(User user) {
                // Delete existing refresh token for user
                refreshTokenRepository.deleteByUserId(user.getId());

                // Create new refresh token
                RefreshToken refreshToken = new RefreshToken();
                refreshToken.setUserId(user.getId());
                refreshToken.setToken(UUID.randomUUID().toString());
                refreshToken.setExpiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration));

                refreshToken = refreshTokenRepository.save(refreshToken);
                return refreshToken.getToken();
        }

        /**
         * Get user's full name based on their role
         * - Regular users: from UserProfile
         * - Mentors: from MentorProfile
         * - Recruiters: from User entity (firstName + lastName)
         */
        private String getUserFullName(User user) {
                try {
                        switch (user.getPrimaryRole()) {
                                case USER:
                                        if (userProfileService.hasProfile(user.getId())) {
                                                var profile = userProfileService.getProfile(user.getId());
                                                return profile.getFullName();
                                        }
                                        break;
                                case MENTOR:
                                        var mentorProfile = mentorProfileRepository.findByUserId(user.getId());
                                        if (mentorProfile.isPresent()) {
                                                return mentorProfile.get().getFullName();
                                        }
                                        break;
                                case RECRUITER:
                                        // For recruiters, construct full name from User entity
                                        String firstName = user.getFirstName();
                                        String lastName = user.getLastName();
                                        if (firstName != null && lastName != null) {
                                                return firstName + " " + lastName;
                                        } else if (firstName != null) {
                                                return firstName;
                                        } else if (lastName != null) {
                                                return lastName;
                                        }
                                        break;
                                case ADMIN:
                                        // For admins, construct full name from User entity
                                        String adminFirstName = user.getFirstName();
                                        String adminLastName = user.getLastName();
                                        if (adminFirstName != null && adminLastName != null) {
                                                return adminFirstName + " " + adminLastName;
                                        } else if (adminFirstName != null) {
                                                return adminFirstName;
                                        } else if (adminLastName != null) {
                                                return adminLastName;
                                        }
                                        break;
                        }
                } catch (Exception e) {
                        log.debug("Could not retrieve full name for user {} with role {}: {}",
                                        user.getId(), user.getPrimaryRole(), e.getMessage());
                }
                return null;
        }
}
