package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.*;
import com.exe.skillverse_backend.auth_service.repository.*;
import com.exe.skillverse_backend.auth_service.dto.request.CompleteProfileRequest;
import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.request.RegisterRequest;
import com.exe.skillverse_backend.auth_service.dto.request.SimpleRegisterRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.dto.response.EmailVerifiedResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.shared.service.AuditService;
import com.exe.skillverse_backend.shared.service.EmailService;
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
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;

    @Value("${jwt.secret:mySecretKey}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:3600}") // 1 hour
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:86400}") // 24 hours
    private Long refreshTokenExpiration;

    @Transactional
    public RegistrationResponse simpleRegister(SimpleRegisterRequest request) {
        try {
            log.info("Starting simple registration for email: {}", request.getEmail());

            // Check if user already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                log.warn("Registration failed - email already exists: {}", request.getEmail());
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }

            // Check if USER role exists
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> {
                        log.error("USER role not found in database. Please ensure roles are initialized.");
                        return new IllegalStateException(
                                "USER role not found in database. Please restart the application to initialize roles.");
                    });

            log.info("Found USER role with ID: {}", userRole.getId());

            // Create user with UNVERIFIED status - email + password only
            User user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .status(UserStatus.UNVERIFIED)
                    .isEmailVerified(false)
                    .build();

            // Assign only USER role
            user.getRoles().add(userRole);

            user = userRepository.save(user);
            log.info("User created with UNVERIFIED status, ID: {}", user.getId());

            // Generate OTP for email verification
            String otp = emailVerificationService.generateOtpForUser(request.getEmail());

            // Log action
            auditService.logAction(user.getId(), "SIMPLE_REGISTER", "USER", user.getId().toString(),
                    "User registered with email: " + request.getEmail() + " - awaiting email verification");

            return RegistrationResponse.builder()
                    .message("Registration successful! Please check your email for verification code.")
                    .email(request.getEmail())
                    .requiresVerification(true)
                    .otpExpiryMinutes(10)
                    .nextStep("Use /api/auth/verify-email endpoint with the OTP sent to your email")
                    .build();

        } catch (Exception e) {
            auditService.logSystemAction("SIMPLE_REGISTER_FAILED", "USER", null,
                    "Simple registration failed for email: " + request.getEmail() + ", error: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public RegistrationResponse registerWithEmailVerification(RegisterRequest request) {
        try {
            log.info("Starting registration with email verification for email: {}", request.getEmail());

            // Check if user already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                log.warn("Registration failed - email already exists: {}", request.getEmail());
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }

            // Check if USER role exists
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> {
                        log.error("USER role not found in database. Please ensure roles are initialized.");
                        return new IllegalStateException(
                                "USER role not found in database. Please restart the application to initialize roles.");
                    });

            log.info("Found USER role with ID: {}", userRole.getId());

            // Create user (NOT VERIFIED yet)
            User user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .status(UserStatus.UNVERIFIED)
                    .isEmailVerified(false)
                    .build();

            // Assign default role
            user.getRoles().add(userRole);

            user = userRepository.save(user);
            log.info("Unverified user saved with ID: {}", user.getId());

            // Generate OTP for email verification
            String otp = emailVerificationService.generateOtpForUser(request.getEmail());

            // TODO: Send OTP via email service (will be implemented next)
            log.info("Generated OTP for user {}: {} (This should be sent via email)", request.getEmail(), otp);

            // Log action
            auditService.logAction(user.getId(), "REGISTER_PENDING", "USER", user.getId().toString(),
                    "User registered, pending email verification: " + request.getEmail());

            return RegistrationResponse.builder()
                    .message("Registration successful! Please check your email for verification code.")
                    .email(request.getEmail())
                    .requiresVerification(true)
                    .otpExpiryMinutes(10)
                    .nextStep("Use /api/auth/verify-email endpoint with the OTP sent to your email")
                    .build();

        } catch (Exception e) {
            auditService.logSystemAction("REGISTER_FAILED", "USER", null,
                    "Registration failed for email: " + request.getEmail() + ", error: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public RegistrationResponse verifyEmailAndActivate(String email, String otp) {
        try {
            log.info("Verifying email and activating user: {}", email);

            // Verify the OTP
            emailVerificationService.verifyOtp(email, otp);

            // Find the user and activate account
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Activate user account - now they can login
            user.setStatus(UserStatus.ACTIVE);
            user = userRepository.save(user);

            // Send welcome email
            emailService.sendWelcomeEmail(email, null);

            // Log successful verification
            auditService.logAction(user.getId(), "EMAIL_VERIFIED_ACTIVATED", "USER", user.getId().toString(),
                    "Email verified and user activated: " + email);

            return RegistrationResponse.builder()
                    .message("Email verified successfully! You can now login with your credentials.")
                    .email(email)
                    .requiresVerification(false)
                    .otpExpiryMinutes(0)
                    .nextStep("Use /api/auth/login to get your access tokens")
                    .build();

        } catch (Exception e) {
            auditService.logSystemAction("EMAIL_VERIFICATION_FAILED", "USER", null,
                    "Email verification failed for: " + email + ", error: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public AuthResponse completeProfileAndLogin(CompleteProfileRequest request) {
        try {
            log.info("Completing profile and logging in user: {}", request.getEmail());

            // Find user and ensure email is verified
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!user.isEmailVerified()) {
                throw new RuntimeException("Email not verified. Please verify your email first.");
            }

            // Check if profile already exists
            if (userProfileService.hasProfile(user.getId())) {
                throw new RuntimeException("Profile already completed. Use login endpoint instead.");
            }

            // Create complete user profile
            userProfileService.createCompleteProfile(
                    user.getId(),
                    request.getFullName(),
                    request.getPhone(),
                    request.getAddress(),
                    request.getRegion(),
                    request.getBio(),
                    request.getAvatarMediaId(),
                    request.getCompanyId(),
                    request.getSocialLinks());

            // NOW activate the user account
            user.setStatus(UserStatus.ACTIVE);
            user = userRepository.save(user);

            // Send welcome email
            emailService.sendWelcomeEmail(request.getEmail(), request.getFullName());

            // Generate tokens for completed user
            String accessToken = generateToken(user);
            String refreshToken = generateRefreshToken(user);

            // Log successful profile completion and login
            auditService.logAction(user.getId(), "PROFILE_COMPLETED_LOGIN", "USER", user.getId().toString(),
                    "Profile completed and user logged in: " + request.getEmail());

            // Build user DTO with profile info
            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setEmail(user.getEmail());
            userDto.setFullName(request.getFullName());
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
            auditService.logSystemAction("PROFILE_COMPLETION_FAILED", "USER", null,
                    "Profile completion failed for: " + request.getEmail() + ", error: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public AuthResponse verifyEmailAndLogin(String email, String otp) {
        try {
            log.info("Verifying email and logging in user: {}", email);

            // Verify the OTP
            emailVerificationService.verifyOtp(email, otp);

            // Find the user and activate account
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setStatus(UserStatus.ACTIVE); // Activate user account
            user = userRepository.save(user);

            // Send welcome email
            emailService.sendWelcomeEmail(email, null);

            // Generate tokens for verified user
            String accessToken = generateToken(user);
            String refreshToken = generateRefreshToken(user);

            // Log successful verification and login
            auditService.logAction(user.getId(), "EMAIL_VERIFIED", "USER", user.getId().toString(),
                    "Email verified and user logged in: " + email);

            // Build user DTO
            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setEmail(user.getEmail());
            userDto.setFullName(null); // Will be updated when profile is completed
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
            auditService.logSystemAction("EMAIL_VERIFICATION_FAILED", "USER", null,
                    "Email verification failed for: " + email + ", error: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        try {
            log.info("Starting registration for email: {}", request.getEmail());

            // Check if user already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                log.warn("Registration failed - email already exists: {}", request.getEmail());
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }

            // Check if USER role exists
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> {
                        log.error("USER role not found in database. Please ensure roles are initialized.");
                        return new IllegalStateException(
                                "USER role not found in database. Please restart the application to initialize roles.");
                    });

            log.info("Found USER role with ID: {}", userRole.getId());

            // Create user
            User user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .build();

            // Assign default role
            user.getRoles().add(userRole);

            user = userRepository.save(user);
            log.info("User saved with ID: {}", user.getId());

            // Create complete user profile with registration data
            try {
                userProfileService.createCompleteProfile(
                        user.getId(),
                        request.getFullName(),
                        request.getPhone(),
                        request.getAddress(),
                        request.getRegion(),
                        request.getBio(),
                        request.getAvatarMediaId(),
                        request.getCompanyId(),
                        request.getSocialLinks());
                auditService.logAction(user.getId(), "CREATE", "USER_PROFILE", user.getId().toString(),
                        "Complete user profile created during registration for user: " + request.getEmail());
            } catch (Exception profileException) {
                log.error("Failed to create profile for user {}: {}", user.getId(), profileException.getMessage());
                // If profile creation fails, rollback user creation too
                userRepository.delete(user);
                throw new RuntimeException(
                        "Registration failed: Unable to create user profile - " + profileException.getMessage());
            }

            // Generate tokens
            String accessToken = generateToken(user);
            String refreshToken = generateRefreshToken(user);

            // Log action
            auditService.logAction(user.getId(), "REGISTER", "USER", user.getId().toString(),
                    "User registered with email: " + request.getEmail());

            // Build user DTO
            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setEmail(user.getEmail());
            userDto.setFullName(null); // Will be updated when profile is completed
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
            auditService.logSystemAction("REGISTER_FAILED", "USER", null,
                    "Registration failed for email: " + request.getEmail() + ", error: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // Find user by email
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));

            // Check user status
            if (user.getStatus() != UserStatus.ACTIVE) {
                auditService.logAction(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(),
                        "Login attempt with non-active status: " + user.getStatus() + " for email: "
                                + request.getEmail());
                throw new RuntimeException("Account not activated. Please verify your email first.");
            }

            // Check if email is verified
            if (!user.isEmailVerified()) {
                auditService.logAction(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(),
                        "Login attempt with unverified email: " + request.getEmail());
                throw new RuntimeException("Email not verified. Please verify your email before logging in.");
            }

            // Verify password
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                auditService.logAction(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(),
                        "Invalid password attempt for email: " + request.getEmail());
                throw new RuntimeException("Invalid credentials");
            }

            // Generate tokens
            String accessToken = generateToken(user);
            String refreshToken = generateRefreshToken(user);

            // Log successful login
            auditService.logAction(user.getId(), "LOGIN", "USER", user.getId().toString(),
                    "Successful login for email: " + request.getEmail());

            // Get user profile information if exists
            String fullName = null;
            try {
                if (userProfileService.hasProfile(user.getId())) {
                    var profile = userProfileService.getProfile(user.getId());
                    fullName = profile.getFullName();
                }
            } catch (Exception e) {
                log.debug("Could not retrieve profile for user {}: {}", user.getId(), e.getMessage());
            }

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

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(user.getId().toString())
                    .issuer("skillverse")
                    .issueTime(new Date())
                    .expirationTime(expirationDate)
                    .jwtID(jti)
                    .claim("email", user.getEmail())
                    .claim("scope", buildScope(user))
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
     * Upgrade user role (e.g., USER → MENTOR)
     */
    @Transactional
    public AuthResponse upgradeUserRole(Long userId, String newRoleName) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Role newRole = roleRepository.findByName(newRoleName)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + newRoleName));

            // Check if user already has this role
            if (user.getRoles().contains(newRole)) {
                throw new RuntimeException("User already has role: " + newRoleName);
            }

            // Add new role (keep existing roles)
            user.getRoles().add(newRole);
            user = userRepository.save(user);

            // Log role upgrade
            auditService.logAction(userId, "ROLE_UPGRADE", "USER", userId.toString(),
                    "User role upgraded to: " + newRoleName);

            // Generate new tokens with updated roles
            String accessToken = generateToken(user);
            String refreshToken = generateRefreshToken(user);

            // Get user profile
            String fullName = null;
            try {
                if (userProfileService.hasProfile(userId)) {
                    var profile = userProfileService.getProfile(userId);
                    fullName = profile.getFullName();
                }
            } catch (Exception e) {
                log.debug("Could not retrieve profile for user {}: {}", userId, e.getMessage());
            }

            // Build updated user DTO
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
            auditService.logSystemAction("ROLE_UPGRADE_FAILED", "USER", userId != null ? userId.toString() : null,
                    "Role upgrade failed for user " + userId + " to role " + newRoleName + ": " + e.getMessage());
            throw e;
        }
    }
}
