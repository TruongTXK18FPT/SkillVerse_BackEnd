package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.*;
import com.exe.skillverse_backend.auth_service.repository.*;
import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.request.RegisterRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.shared.service.AuditService;
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

    @Value("${jwt.secret:mySecretKey}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:3600}") // 1 hour
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:86400}") // 24 hours
    private Long refreshTokenExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        try {
            // Check if user already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists");
            }

            // Create user
            User user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .build();

            // Assign default role
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("Default role not found"));
            user.getRoles().add(userRole);

            user = userRepository.save(user);

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
            // Find user
            User user = userRepository.findByEmailAndIsActiveTrue(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));

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
}
