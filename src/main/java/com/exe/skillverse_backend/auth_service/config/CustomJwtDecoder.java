package com.exe.skillverse_backend.auth_service.config;

import com.exe.skillverse_backend.auth_service.repository.InvalidatedTokenRepository;
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
import java.time.Instant;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomJwtDecoder implements JwtDecoder {

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:3600}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:86400}")
    private long refreshTokenExpiration;

    @Value("${jwt.issuer:skillverse}")
    private String expectedIssuer;
    @Value("${jwt.audience:}")
    private String expectedAudience; // optional

    // allow small clock skew
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    // Getter methods for JWT configuration
    public String getJwtSecret() {
        return jwtSecret;
    }

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

            // Optional audience check
            if (expectedAudience != null && !expectedAudience.isEmpty()) {
                var audiences = signedJWT.getJWTClaimsSet().getAudience();
                if (audiences == null || audiences.stream().noneMatch(a -> expectedAudience.equals(a))) {
                    throw new JwtException("Invalid token audience");
                }
            }

            // Check if token is invalidated
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (jti != null && invalidatedTokenRepository.existsByJti(jti)) {
                throw new JwtException("JWT token has been invalidated");
            }

            // Convert to Spring Security Jwt
            return createJwt(signedJWT);

        } catch (Exception e) {
            log.error("Error decoding JWT token", e);
            throw new JwtException("Error decoding JWT token: " + e.getMessage());
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