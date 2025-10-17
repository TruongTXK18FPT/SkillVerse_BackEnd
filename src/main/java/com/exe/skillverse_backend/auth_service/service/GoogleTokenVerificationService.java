package com.exe.skillverse_backend.auth_service.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.Map;

/**
 * Service for verifying Google OAuth tokens and fetching user information.
 * Supports both ID Token verification and Access Token user info fetching.
 */
@Slf4j
@Service
public class GoogleTokenVerificationService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    private final RestTemplate restTemplate;

    /**
     * Constructor for GoogleTokenVerificationService.
     * Creates a RestTemplate instance for making HTTP requests to Google APIs.
     */
    public GoogleTokenVerificationService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Get user info from Google using access token.
     * This is used when frontend sends access_token instead of id_token.
     * 
     * @param accessToken The access token from Google OAuth
     * @return Map containing user info (email, name, picture, etc.)
     * @throws Exception if token is invalid or API call fails
     */
    public Map<String, Object> getUserInfoFromAccessToken(String accessToken) throws Exception {
        log.info("Fetching user info from Google using access token");

        String url = "https://www.googleapis.com/oauth2/v2/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken); // Proper Bearer token authentication
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userInfo = (Map<String, Object>) response.getBody();

                // ✅ SECURITY: Validate required fields from Google response
                if (userInfo.get("email") == null) {
                    log.error("Google API response missing email field");
                    throw new IllegalArgumentException("Invalid Google API response: missing email");
                }

                log.info("User info fetched successfully for email: {}", userInfo.get("email"));
                return userInfo;
            } else {
                log.error("Failed to fetch user info from Google. Status: {}", response.getStatusCode());
                throw new IllegalArgumentException("Failed to fetch user info from Google");
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Unauthorized: Invalid or expired access token");
            throw new IllegalArgumentException("Invalid or expired access token", e);
        } catch (HttpClientErrorException e) {
            log.error("HTTP error from Google API: {} - {}", e.getStatusCode(), e.getMessage());
            throw new IllegalArgumentException("Google API error: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Network error calling Google API: {}", e.getMessage());
            throw new IllegalArgumentException("Network error contacting Google", e);
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.error("Unexpected error fetching user info from Google: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies a Google ID Token and returns the payload containing user
     * information.
     * This method is kept for backward compatibility or when using ID tokens.
     * 
     * @param idTokenString The ID token string from Google OAuth
     * @return GoogleIdToken.Payload containing user info (email, name, picture,
     *         etc.)
     * @throws Exception if token is invalid or verification fails
     */
    public GoogleIdToken.Payload verifyIdToken(String idTokenString) throws Exception {
        log.info("Verifying Google ID token");

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            log.info("Token verified successfully for email: {}", payload.getEmail());
            return payload;
        } else {
            log.error("Invalid Google ID token");
            throw new IllegalArgumentException("Invalid Google ID token");
        }
    }
}
