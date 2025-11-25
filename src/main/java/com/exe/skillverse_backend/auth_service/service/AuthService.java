package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.*;
import com.exe.skillverse_backend.auth_service.repository.*;
import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.shared.exception.AccountPendingApprovalException;
import com.exe.skillverse_backend.shared.exception.AuthenticationException;
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
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final RefreshTokenRepository refreshTokenRepository;
        private final InvalidatedTokenRepository invalidatedTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final UserProfileService userProfileService;
        private final MentorProfileRepository mentorProfileRepository;
        private final RecruiterProfileRepository recruiterProfileRepository;
        private final EmailVerificationService emailVerificationService;
        private final EmailService emailService;
        private final PremiumPlanRepository premiumPlanRepository;
        private final UserSubscriptionRepository userSubscriptionRepository;
        private final GoogleTokenVerificationService googleTokenVerificationService;

        @Value("${jwt.secret}")
        private String jwtSecret;

        @Value("${jwt.access-token-expiration}") // 1 hour
        private Long accessTokenExpiration;

        @Value("${jwt.refresh-token-expiration}") // 24 hours
        private Long refreshTokenExpiration;

        @Value("${jwt.refresh-pepper:skillverse-refresh-pepper}")
        private String refreshPepper;

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
                        String actionType = user.getPrimaryRole() == PrimaryRole.USER ? "EMAIL_VERIFIED_ACTIVATED"
                                        : "EMAIL_VERIFIED_PENDING_APPROVAL";
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
                                        .otpExpiryTime(null)
                                        .nextStep(nextStep)
                                        .build();

                } catch (Exception e) {
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
                                throw new AuthenticationException("Invalid credentials");
                        }

                        // Generate tokens
                        String accessToken = generateToken(user);
                        String refreshToken = generateRefreshToken(user);
                        // Get user profile information if exists
                        String fullName = getUserFullName(user);
                        String avatarUrl = getUserAvatarUrl(user);

                        // Build user DTO
                        UserDto userDto = new UserDto();
                        userDto.setId(user.getId());
                        userDto.setEmail(user.getEmail());
                        userDto.setFullName(fullName);
                        userDto.setAvatarUrl(avatarUrl);
                        userDto.setRoles(user.getRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(Collectors.toSet()));
                        userDto.setAuthProvider(user.getAuthProvider().toString());
                        userDto.setGoogleLinked(user.isGoogleLinked());

                        return AuthResponse.builder()
                                        .accessToken(accessToken)
                                        .refreshToken(refreshToken)
                                        .tokenType("Bearer")
                                        .expiresIn(accessTokenExpiration)
                                        .user(userDto)
                                        .build();

                } catch (Exception e) {
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
                        log.info("Starting token refresh process");

                        // Lookup by HASH first (new scheme)
                        String providedHash = hashRefreshToken(refreshToken);
                        Optional<RefreshToken> byHash = refreshTokenRepository.findByToken(providedHash);
                        RefreshToken tokenRecord;

                        if (byHash.isPresent()) {
                                tokenRecord = byHash.get();
                        } else {
                                // Backward compatibility: lookup plaintext (legacy)
                                Optional<RefreshToken> legacy = refreshTokenRepository.findByToken(refreshToken);
                                if (legacy.isEmpty()) {
                                        log.error("Refresh token not found (hash and legacy)");
                                        throw new RuntimeException("Invalid refresh token");
                                }
                                // Migrate to hash in-place
                                RefreshToken migrated = legacy.get();
                                migrated.setToken(providedHash);
                                tokenRecord = refreshTokenRepository.save(migrated);
                        }

                        // Check expiration
                        if (tokenRecord.getExpiryDate().isBefore(LocalDateTime.now())) {
                                log.warn("Refresh token expired for user ID: {}", tokenRecord.getUserId());
                                refreshTokenRepository.delete(tokenRecord);
                                throw new RuntimeException("Refresh token expired");
                        }

                        // Reuse detection: if provided token cannot be found by current hash (handled
                        // above)
                        // or if multiple valid tokens exist for same user (shouldn't due to
                        // deleteByUserId),
                        // we could enforce additional policies; current rotation + single-token per
                        // user prevents reuse window.

                        // Get user
                        User user = userRepository.findById(tokenRecord.getUserId())
                                        .orElseThrow(() -> {
                                                log.error("User not found for refresh token, user ID: {}",
                                                                tokenRecord.getUserId());
                                                return new RuntimeException("User not found");
                                        });

                        log.info("Refreshing tokens for user: {} (ID: {})", user.getEmail(), user.getId());

                        // Generate new tokens (absolute lifetime enforcement: do not extend beyond
                        // current expiry)
                        String newAccessToken = generateToken(user);
                        String newRefreshToken = generateRefreshToken(user, tokenRecord.getExpiryDate());

                        // Delete old refresh token
                        refreshTokenRepository.delete(tokenRecord);

                        // Get user profile information
                        String fullName = getUserFullName(user);
                        String avatarUrl = getUserAvatarUrl(user);

                        // Build user DTO
                        UserDto userDto = new UserDto();
                        userDto.setId(user.getId());
                        userDto.setEmail(user.getEmail());
                        userDto.setFullName(fullName);
                        userDto.setAvatarUrl(avatarUrl);
                        userDto.setRoles(user.getRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(Collectors.toSet()));
                        userDto.setAuthProvider(user.getAuthProvider().toString());
                        userDto.setGoogleLinked(user.isGoogleLinked());
                        log.info("Token refresh successful for user: {}", user.getEmail());

                        return AuthResponse.builder()
                                        .accessToken(newAccessToken)
                                        .refreshToken(newRefreshToken)
                                        .tokenType("Bearer")
                                        .expiresIn(accessTokenExpiration)
                                        .user(userDto) // ✅ FIX: Add user data to response
                                        .build();

                } catch (Exception e) {
                        log.error("Token refresh failed: {}", e.getMessage(), e);
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
                } catch (Exception e) {
                        log.error("Error during logout", e);
                        throw new RuntimeException("Error during logout");
                }
        }

        public String buildScope(User user) {
                return user.getRoles().stream()
                                .map(role -> "ROLE_" + role.getName())
                                .collect(Collectors.joining(" "));
        }

        private String generateRefreshToken(User user) {
                return generateRefreshToken(user, null);
        }

        private String generateRefreshToken(User user, LocalDateTime legacyExpiry) {
                // Delete existing refresh token for user
                refreshTokenRepository.deleteByUserId(user.getId());

                // Create new refresh token (plaintext to return to client)
                String plain = UUID.randomUUID().toString();
                String tokenHash = hashRefreshToken(plain);

                RefreshToken refreshToken = new RefreshToken();
                refreshToken.setUserId(user.getId());
                refreshToken.setToken(tokenHash); // store hash only
                // Absolute lifetime: do not extend beyond previous expiry when rotating via
                // refresh
                LocalDateTime newExpiry = LocalDateTime.now().plusSeconds(refreshTokenExpiration);
                if (legacyExpiry != null && legacyExpiry.isBefore(newExpiry)) {
                        newExpiry = legacyExpiry; // cap to legacy expiry
                }
                refreshToken.setExpiryDate(newExpiry);

                refreshTokenRepository.save(refreshToken);
                return plain; // return plaintext to client
        }

        private String hashRefreshToken(String token) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] bytes = digest.digest((refreshPepper + ":" + token).getBytes(StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        for (byte b : bytes)
                                sb.append(String.format("%02x", b));
                        return sb.toString();
                } catch (Exception e) {
                        throw new RuntimeException("Failed to hash refresh token");
                }
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

        /**
         * Get user's avatar URL from their profile
         */
        private String getUserAvatarUrl(User user) {
                try {
                        if (user.getAvatarUrl() != null) {
                                return user.getAvatarUrl();
                        }

                        // Try to get from UserProfile if exists
                        if (userProfileService.hasProfile(user.getId())) {
                                var profile = userProfileService.getProfile(user.getId());
                                if (profile.getAvatarMediaUrl() != null) {
                                        return profile.getAvatarMediaUrl();
                                }
                        }
                } catch (Exception e) {
                        log.warn("Failed to get avatar URL for user {}: {}", user.getId(), e.getMessage());
                }
                return null;
        }

        /**
         * Authenticate user with Google OAuth.
         * If user doesn't exist, creates a new USER account.
         * Only USER role can login with Google (MENTOR/BUSINESS must use local auth).
         * 
         * @param idToken Google ID Token from frontend
         * @return AuthResponse with JWT tokens and user info
         * @throws RuntimeException if authentication fails
         */
        @Transactional
        public AuthResponse authenticateWithGoogle(String idToken) {
                try {
                        log.info("Starting Google authentication");

                        // ✅ SECURITY: Validate input token
                        if (idToken == null || idToken.trim().isEmpty()) {
                                log.error("Empty or null access token provided");
                                throw new IllegalArgumentException("Access token is required");
                        }

                        if (idToken.length() > 2048) { // Reasonable token size limit
                                log.error("Access token too long: {} characters", idToken.length());
                                throw new IllegalArgumentException("Invalid access token format");
                        }

                        // 1. Get user info from Google using access token
                        // Note: idToken parameter actually contains access_token from frontend
                        Map<String, Object> userInfo = googleTokenVerificationService
                                        .getUserInfoFromAccessToken(idToken);

                        String email = (String) userInfo.get("email");
                        String name = (String) userInfo.get("name");
                        String picture = (String) userInfo.get("picture");
                        Boolean emailVerified = (Boolean) userInfo.get("verified_email");

                        log.info("Google user info fetched for email: {}", email);
                        log.info("📸 Picture URL from Google: {}", picture);

                        // ✅ SECURITY: Validate email format from Google
                        if (email == null || email.isEmpty()) {
                                log.error("Email not provided by Google");
                                throw new RuntimeException("Email not provided by Google");
                        }

                        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                                log.error("Invalid email format from Google: {}", email);
                                throw new RuntimeException("Invalid email format");
                        }

                        // Google OAuth users are already verified
                        if (emailVerified != null && !emailVerified) {
                                log.warn("Email not verified by Google for: {}", email);
                                // Continue anyway as Google OAuth implies verification
                        }

                        // 2. Check if user exists
                        Optional<User> existingUser = userRepository.findByEmail(email);

                        User user;
                        boolean isNewUser = false;

                        if (existingUser.isEmpty()) {
                                log.info("Creating new user from Google login: {}", email);

                                // 3. Create new user with USER role only
                                user = User.builder()
                                                .email(email)
                                                .password(null) // No password for Google users
                                                .avatarUrl(picture) // ✅ Save Google avatar URL
                                                .primaryRole(PrimaryRole.USER)
                                                .status(UserStatus.ACTIVE)
                                                .isEmailVerified(true)
                                                .authProvider(AuthProvider.GOOGLE)
                                                .googleLinked(false) // ❌ NOT linked yet - user hasn't set password
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build();

                                user = userRepository.save(user);

                                // ✅ CRITICAL: Assign USER role to Google user
                                Role userRole = roleRepository.findByName("USER")
                                                .orElseThrow(() -> new RuntimeException(
                                                                "USER role not found in database"));
                                user.getRoles().add(userRole);
                                user = userRepository.saveAndFlush(user); // Force flush to DB immediately

                                log.info("User role assigned and flushed to database for: {}", email);

                                // ✅ Force reload user with roles populated (fix Hibernate lazy loading)
                                user = userRepository.findById(user.getId())
                                                .orElseThrow(() -> new RuntimeException(
                                                                "User not found after creation"));

                                log.info("User reloaded with {} roles", user.getRoles().size());

                                isNewUser = true;
                                log.info("Created new Google user successfully: {}", email);

                                // ✅ Auto-assign FREE_TIER subscription to new Google user
                                try {
                                        assignFreeTierSubscription(user);
                                } catch (Exception e) {
                                        log.error("Failed to assign FREE_TIER subscription to Google user: {}", email,
                                                        e);
                                        // Don't fail authentication - user can be assigned subscription later
                                }

                                // ✅ BEST PRACTICE: Create UserProfile in separate transaction
                                // This ensures User creation is committed even if profile creation fails
                                try {
                                        userProfileService.createUserProfileForGoogleUser(user, name, email, picture);
                                } catch (Exception e) {
                                        log.error("Failed to create user profile, but user was created successfully",
                                                        e);
                                        // User can complete profile later - don't fail authentication
                                }

                        } else {
                                user = existingUser.get();
                                log.info("Existing user logging in with Google: {}", email);

                                // 5. Validate: Only USER role can login with Google
                                if (user.getPrimaryRole() != PrimaryRole.USER) {
                                        log.error("Non-USER role attempted Google login: {} with role {}",
                                                        email, user.getPrimaryRole());
                                        throw new RuntimeException("Only USER accounts can login with Google. " +
                                                        "MENTOR and BUSINESS accounts must use email/password login.");
                                }

                                // 6. Check account status
                                if (user.getStatus() != UserStatus.ACTIVE) {
                                        log.error("Inactive user attempted Google login: {}", email);
                                        throw new RuntimeException(
                                                        "Your account is not active. Please contact support.");
                                }

                                // ✅ Update avatar URL from Google if available
                                if (picture != null && !picture.isEmpty()) {
                                        user.setAvatarUrl(picture);
                                        log.info("Updated avatar URL from Google for user: {}", email);
                                }

                                // 7. Link Google account if not already linked
                                if (!user.isGoogleLinked()) {
                                        log.info("Linking Google account to existing {} user: {}",
                                                        user.getAuthProvider(), email);

                                        // ✅ NEW APPROACH: Enable dual authentication
                                        // - Keep authProvider as-is (LOCAL or GOOGLE)
                                        // - Set googleLinked = true to allow Google login
                                        // - Keep password intact (for LOCAL users)
                                        user.setGoogleLinked(true);
                                        user.setUpdatedAt(java.time.LocalDateTime.now());
                                        userRepository.save(user);
                                        log.info("Google account linked successfully. User can now login with both methods.");
                                }
                        }

                        // 8. Generate JWT tokens
                        String accessToken = generateToken(user);
                        String refreshToken = generateRefreshToken(user);

                        // 9. Get user full name and avatar
                        String fullName = isNewUser ? name : getUserFullName(user);
                        String avatarUrl = getUserAvatarUrl(user);

                        log.info("🖼️ Avatar URL for user {}: {}", email, avatarUrl);
                        log.info("🖼️ User.avatarUrl from entity: {}", user.getAvatarUrl());

                        // 10. Build user DTO
                        UserDto userDto = new UserDto();
                        userDto.setId(user.getId());
                        userDto.setEmail(user.getEmail());
                        userDto.setFullName(fullName);
                        userDto.setAvatarUrl(avatarUrl);
                        userDto.setRoles(user.getRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(Collectors.toSet()));
                        userDto.setAuthProvider(user.getAuthProvider().toString());
                        userDto.setGoogleLinked(user.isGoogleLinked());

                        // 11. Build response
                        AuthResponse response = AuthResponse.builder()
                                        .accessToken(accessToken)
                                        .refreshToken(refreshToken)
                                        .tokenType("Bearer")
                                        .expiresIn(accessTokenExpiration)
                                        .user(userDto)
                                        .needsProfileCompletion(isNewUser)
                                        .build();

                        log.info("Google authentication successful for user: {}", email);
                        return response;

                } catch (IllegalArgumentException e) {
                        log.error("Invalid Google token: {}", e.getMessage());
                        throw new RuntimeException("Invalid Google ID token: " + e.getMessage());
                } catch (Exception e) {
                        log.error("Google authentication failed", e);
                        throw new RuntimeException("Google authentication failed: " + e.getMessage());
                }
        }

        /**
         * Auto-assign FREE_TIER subscription to new user
         */
        private void assignFreeTierSubscription(User user) {
                try {
                        // Find FREE_TIER plan
                        PremiumPlan freeTier = premiumPlanRepository
                                        .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER)
                                        .orElseThrow(() -> new IllegalStateException("FREE_TIER plan not found"));

                        // Create permanent subscription for FREE_TIER
                        UserSubscription subscription = UserSubscription.builder()
                                        .user(user)
                                        .plan(freeTier)
                                        .startDate(LocalDateTime.now())
                                        .endDate(LocalDateTime.now().plusYears(100)) // Permanent
                                        .isActive(true)
                                        .autoRenew(false)
                                        .isStudentSubscription(false)
                                        .status(UserSubscription.SubscriptionStatus.ACTIVE)
                                        .build();

                        userSubscriptionRepository.save(subscription);
                        log.info("✅ Auto-assigned FREE_TIER subscription to user: {} (ID: {})", user.getEmail(),
                                        user.getId());

                } catch (Exception e) {
                        log.error("❌ Failed to assign FREE_TIER subscription to user: {}", user.getEmail(), e);
                        // Don't throw exception - user creation should succeed even if subscription
                        // fails
                }
        }
}
