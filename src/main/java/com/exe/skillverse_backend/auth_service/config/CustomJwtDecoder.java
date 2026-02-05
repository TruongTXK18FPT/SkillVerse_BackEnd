package com.exe.skillverse_backend.auth_service.config;

import com.exe.skillverse_backend.auth_service.repository.InvalidatedTokenRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomJwtDecoder implements JwtDecoder {

    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:3600}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:86400}")
    private long refreshTokenExpiration;

    @Value("${jwt.issuer:skillverse}")
    private String expectedIssuer;

    // allow small clock skew
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    // ✅ SECURITY: Removed public getJwtSecret() - secret should never be exposed
    // Getters for non-sensitive config only
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            // Parse the JWT
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Verify signature - MUST use getBytes() to match signing
            MACVerifier verifier = new MACVerifier(jwtSecret.getBytes());
            if (!signedJWT.verify(verifier)) {
                throw new JwtException("Invalid JWT signature");
            }

            // Check expiration with small leeway
            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime != null) {
                Instant exp = expirationTime.toInstant();
                if (exp.isBefore(Instant.now().minus(CLOCK_SKEW))) {
                    throw new JwtException("JWT token expired");
                }
            }

            // Validate not-before if present (with leeway)
            Date notBefore = signedJWT.getJWTClaimsSet().getNotBeforeTime();
            if (notBefore != null) {
                Instant nbf = notBefore.toInstant();
                if (nbf.isAfter(Instant.now().plus(CLOCK_SKEW))) {
                    throw new JwtException("JWT token not active yet");
                }
            }

            // Validate issuer
            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (expectedIssuer != null && !expectedIssuer.isEmpty()) {
                if (issuer == null || !expectedIssuer.equals(issuer)) {
                    throw new JwtException("Invalid token issuer");
                }
            }

            // Check if token is invalidated
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (jti != null && invalidatedTokenRepository.existsByJti(jti)) {
                throw new JwtException("JWT token has been invalidated");
            }

            // ✅ SECURITY: Check if token was issued before password change
            // Tokens issued before passwordChangedAt are invalid (user changed password)
            // [OPTIMIZED] Uses projection query instead of loading full User entity
            // [TIMEZONE-SAFE] Uses UTC for both JWT iat and passwordChangedAt comparison
            String userId = signedJWT.getJWTClaimsSet().getSubject();
            Date issuedAt = signedJWT.getJWTClaimsSet().getIssueTime();
            if (userId != null && issuedAt != null) {
                try {
                    Long userIdLong = Long.parseLong(userId);
                    Optional<LocalDateTime> passwordChangedAtOpt = userRepository.findPasswordChangedAtById(userIdLong);
                    
                    if (passwordChangedAtOpt.isPresent()) {
                        LocalDateTime passwordChangedAt = passwordChangedAtOpt.get();
                        // Convert issuedAt to LocalDateTime using UTC for consistent comparison
                        // Both JWT iat and passwordChangedAt are stored/compared in UTC
                        Instant iatInstant = issuedAt.toInstant();
                        LocalDateTime iatDateTime = LocalDateTime.ofInstant(iatInstant, ZoneId.of("UTC"));
                        
                        // If token was issued before password change, reject it
                        // Allow CLOCK_SKEW tolerance to handle minor time differences
                        if (iatDateTime.isBefore(passwordChangedAt.minus(CLOCK_SKEW))) {
                            log.warn("Token issued before password change for user {}. Token iat (UTC): {}, Password changed at (UTC): {}", 
                                    userId, iatDateTime, passwordChangedAt);
                            throw new JwtException("Token invalidated due to password change. Please login again.");
                        }
                    }
                } catch (NumberFormatException e) {
                    // userId is not a number, skip password change check
                    log.debug("Could not parse userId as Long: {}", userId);
                }
            }

            // Convert to Spring Security Jwt
            return createJwt(signedJWT);

        } catch (JwtException e) {
            // Re-throw JWT exceptions with their user-friendly messages
            throw e;
        } catch (Exception e) {
            // ✅ SECURITY: Log full error server-side, return generic message to client
            log.error("Error decoding JWT token", e);
            throw new JwtException("Invalid or malformed token");
        }
    }

    private Jwt createJwt(SignedJWT signedJWT) throws Exception {
        var claimsSet = signedJWT.getJWTClaimsSet();

        Jwt.Builder jwtBuilder = Jwt.withTokenValue(signedJWT.serialize())
                .header("alg", signedJWT.getHeader().getAlgorithm().getName())
                .header("typ", "JWT");

        // Add claims
        claimsSet.getClaims().forEach((key, value) -> {
            if ("exp".equals(key) && value instanceof Date) {
                jwtBuilder.expiresAt(((Date) value).toInstant());
            } else if ("iat".equals(key) && value instanceof Date) {
                jwtBuilder.issuedAt(((Date) value).toInstant());
            } else if ("nbf".equals(key) && value instanceof Date) {
                jwtBuilder.notBefore(((Date) value).toInstant());
            } else {
                jwtBuilder.claim(key, value);
            }
        });

        return jwtBuilder.build();
    }
}