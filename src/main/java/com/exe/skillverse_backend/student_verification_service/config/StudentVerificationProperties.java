package com.exe.skillverse_backend.student_verification_service.config;

import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStorageProvider;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "student-verification")
@Getter
@Setter
public class StudentVerificationProperties {

    private boolean enabled = true;

    private StudentVerificationStorageProvider storageProvider = StudentVerificationStorageProvider.CLOUDINARY;

    private String cloudinaryFolder = "skillverse/student-verification";

    private String localStoragePath = "uploads/student-verification";

    private long maxFileSizeBytes = 10L * 1024 * 1024;

    private List<String> allowedMimeTypes = new ArrayList<>(List.of("image/jpeg", "image/png"));

    /**
     * Allowed school email domains (exact domain values, e.g. fpt.edu.vn).
     */
    private List<String> allowedSchoolDomains = new ArrayList<>();

    /**
     * Additional allowed suffixes, used when exact whitelist is empty or not matched.
     */
    private List<String> allowedDomainSuffixes = new ArrayList<>(List.of(".edu.vn"));

    private Otp otp = new Otp();

    @Getter
    @Setter
    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 10;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;
    }
}
