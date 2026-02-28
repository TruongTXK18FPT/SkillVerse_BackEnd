package com.exe.skillverse_backend.shared.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Shared utility for extracting user identity from JWT tokens.
 * <p>
 * Centralises claim extraction so all controllers use identical, null-safe logic.
 * Strategy:
 *   1. Try the custom {@code "userId"} claim (Long stored as String in our token).
 *   2. Fall back to the standard {@code "sub"} (subject) claim.
 * <p>
 * Use the {@link #extractUserId(Jwt)} overload when the controller already receives
 * a {@link Jwt} via {@code @AuthenticationPrincipal Jwt jwt}.
 * Use the {@link #extractUserId(Authentication)} overload when only an
 * {@link Authentication} object is available (e.g. injected via method parameter).
 */
public final class JwtUtils {

    private JwtUtils() {
        // utility class — no instances
    }

    /**
     * Extract the authenticated user's Long ID from a {@link Jwt} token.
     *
     * @param jwt the injected JWT — must not be null
     * @return the user's numeric ID
     * @throws IllegalArgumentException if neither claim is present or parseable
     */
    public static Long extractUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId != null && !userId.isBlank()) {
            return Long.parseLong(userId);
        }
        // Fallback: "sub" claim (standard JWT subject == userId in our auth server config)
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return Long.parseLong(subject);
        }
        throw new IllegalArgumentException("JWT contains neither 'userId' claim nor 'sub' claim");
    }

    /**
     * Extract the authenticated user's Long ID from a Spring Security {@link Authentication}.
     * <p>
     * Expects the principal to be a {@link JwtAuthenticationToken} (the default when using
     * Spring Security OAuth2 Resource Server with JWT).
     *
     * @param authentication the current authentication — must not be null
     * @return the user's numeric ID
     * @throws IllegalStateException if the authentication is not JWT-based
     */
    public static Long extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return extractUserId(jwtAuth.getToken());
        }
        throw new IllegalStateException(
                "Cannot extract userId: authentication is not a JwtAuthenticationToken (was: "
                + authentication.getClass().getSimpleName() + ")");
    }
}
