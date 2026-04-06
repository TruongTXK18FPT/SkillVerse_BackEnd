package com.exe.skillverse_backend.notification_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials.json-base64:}")
    private String credentialsJsonBase64;

    @Value("${firebase.credentials.json:}")
    private String credentialsJson;

    @Value("${firebase.project-id:skillverse-app}")
    private String projectId;

    private final AtomicBoolean firebaseEnabled = new AtomicBoolean(false);

    @PostConstruct
    public void initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                InputStream serviceAccount = findCredentialsStream();
                if (serviceAccount != null) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount)
                                    .createScoped(Arrays.asList(
                                            "https://www.googleapis.com/auth/cloud-platform",
                                            "https://www.googleapis.com/auth/firebase.messaging"
                                    )))
                            .setProjectId(projectId)
                            .build();
                    FirebaseApp.initializeApp(options);
                    firebaseEnabled.set(true);
                    log.info("Firebase Admin SDK initialized successfully");
                } else {
                    log.warn("Firebase credentials not found. Push notifications will be disabled.");
                    log.warn("Set FIREBASE_CREDENTIALS_JSON_BASE64 env var.");
                }
            } catch (IOException e) {
                log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage());
            }
        } else {
            firebaseEnabled.set(true);
            log.info("Firebase Admin SDK already initialized");
        }
    }

    public boolean isFirebaseEnabled() {
        return firebaseEnabled.get();
    }

    private InputStream findCredentialsStream() {
        // Ưu tiên 1: Base64 encoded
        if (StringUtils.hasText(credentialsJsonBase64)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(credentialsJsonBase64);
                log.info("Firebase credentials loaded from FIREBASE_CREDENTIALS_JSON_BASE64");
                return new ByteArrayInputStream(decoded);
            } catch (IllegalArgumentException e) {
                log.warn("Failed to decode FIREBASE_CREDENTIALS_JSON_BASE64: {}", e.getMessage());
            }
        }

        // Ưu tiên 2: Raw JSON (GitHub Secrets)
        if (StringUtils.hasText(credentialsJson)) {
            log.info("Firebase credentials loaded from FIREBASE_CREDENTIALS_JSON");
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }

        return null;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (!firebaseEnabled.get()) {
            log.warn("Firebase is not initialized. FCM will be disabled.");
            return null;
        }
        return FirebaseMessaging.getInstance();
    }
}
