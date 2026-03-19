package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateIssueRequestDTO;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateVerificationDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import com.exe.skillverse_backend.course_service.entity.Certificate;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.mapper.CertificateMapper;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.CertificateService;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateServiceImpl implements CertificateService {

    private static final String COURSE_NOT_FOUND = "COURSE_NOT_FOUND";
    private static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    private static final String CERTIFICATE_NOT_FOUND = "CERTIFICATE_NOT_FOUND";
    private static final String COURSE_NOT_COMPLETED = "COURSE_NOT_COMPLETED";
    private static final String ISSUER_NAME = "Skillverse";
    private static final String VERIFICATION_STATUS_VALID = "VALID";
    private static final String VERIFICATION_STATUS_REVOKED = "REVOKED";
    private static final String COMPLETION_STATEMENT =
            "This internal certificate confirms that the learner completed the recorded course requirements on Skillverse.";
    private static final String DISCLAIMER =
            "This is a Skillverse course completion certificate. It is not automatically equivalent to an academic degree, state-recognized diploma, or professional license.";
    private static final String SERIAL_PREFIX = "SVC-";
    private static final String SERIAL_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SERIAL_RANDOM_LENGTH = 16;
    private static final int MAX_SERIAL_GENERATION_ATTEMPTS = 5;
    private static final String PLATFORM_PROOF_FIELD = "platformProof";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final CertificateMapper certificateMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Value("${app.certificate.proof-secret:skillverse-certificate-proof-dev-secret}")
    private String certificateProofSecret = "skillverse-certificate-proof-dev-secret";

    @Override
    @Transactional
    public CertificateDTO issueCourseCertificate(
            Long courseId,
            Long userId,
            CourseLearningStatusDTO completionStatus
    ) {
        if (completionStatus.getPercent() < 100) {
            throw new IllegalStateException(COURSE_NOT_COMPLETED);
        }

        Optional<Certificate> activeCertificate = certificateRepository
                .findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(userId, courseId);
        if (activeCertificate.isPresent()) {
            return buildCertificateDto(activeCertificate.get());
        }

        Optional<Certificate> latestCertificate = certificateRepository
                .findFirstByUser_IdAndCourse_IdOrderByIssuedAtDesc(userId, courseId);
        if (latestCertificate.isPresent() && latestCertificate.get().getRevokedAt() != null) {
            log.warn(
                    "Skipped auto-issuing certificate for user {} and course {} because the latest certificate is revoked",
                    userId,
                    courseId
            );
            return buildCertificateDto(latestCertificate.get());
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException(COURSE_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        String recipientNameSnapshot = resolveRecipientName(user);
        String courseTitleSnapshot = resolveCourseTitle(course);
        String instructorNameSnapshot = resolveInstructorName(course);
        String instructorSignatureUrlSnapshot = resolveInstructorSignatureUrl(course);
        Instant issuedAt = Instant.now(clock);

        for (int attempt = 1; attempt <= MAX_SERIAL_GENERATION_ATTEMPTS; attempt++) {
            String serial = generateSerial();
            String criteria = buildCriteriaSnapshot(courseId, userId, completionStatus, serial, issuedAt);
            Certificate certificate = certificateMapper.toEntity(
                    new CertificateIssueRequestDTO(courseId),
                    user,
                    course,
                    serial,
                    recipientNameSnapshot,
                    courseTitleSnapshot,
                    instructorNameSnapshot,
                    instructorSignatureUrlSnapshot,
                    criteria
            );
            certificate.setIssuedAt(issuedAt);

            try {
                Certificate savedCertificate = certificateRepository.save(certificate);
                log.info(
                        "Issued course certificate {} for user {} and course {}",
                        serial,
                        userId,
                        courseId
                );
                return buildCertificateDto(savedCertificate);
            } catch (DataIntegrityViolationException duplicateException) {
                log.warn(
                        "Certificate issuance hit uniqueness guard for user {} and course {} on attempt {}.",
                        userId,
                        courseId,
                        attempt
                );

                Optional<Certificate> existingAfterConflict = certificateRepository
                        .findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(
                                userId,
                                courseId
                        );
                if (existingAfterConflict.isPresent()) {
                    return buildCertificateDto(existingAfterConflict.get());
                }

                if (attempt == MAX_SERIAL_GENERATION_ATTEMPTS) {
                    throw duplicateException;
                }
            }
        }

        throw new IllegalStateException("CERTIFICATE_SERIAL_GENERATION_EXHAUSTED");
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateDTO getUserCertificate(Long certificateId, Long userId) {
        Certificate certificate = certificateRepository.findByIdAndUserId(certificateId, userId)
                .orElseThrow(() -> new NotFoundException(CERTIFICATE_NOT_FOUND));
        return buildCertificateDto(certificate);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateVerificationDTO getCertificateVerification(String serial) {
        Certificate certificate = certificateRepository.findBySerial(serial)
                .orElseThrow(() -> new NotFoundException(CERTIFICATE_NOT_FOUND));

        String instructorName = resolveInstructorDisplayName(certificate);
        String recipientName = resolveRecipientDisplayName(certificate);
        String platformProof = extractStoredPlatformProof(certificate.getCriteria());
        Boolean proofVerified = verifyPlatformProof(certificate, platformProof);
        String courseTitle = certificate.getCourseTitleSnapshot() != null
                ? certificate.getCourseTitleSnapshot()
                : (certificate.getCourse() != null ? certificate.getCourse().getTitle() : null);
        String type = certificate.getType() != null ? certificate.getType().name() : null;

        return CertificateVerificationDTO.builder()
                .serial(certificate.getSerial())
                .courseTitle(courseTitle)
                .recipientName(recipientName)
                .instructorName(instructorName)
                .instructorSignatureUrl(resolveCertificateInstructorSignatureUrl(certificate))
                .issuerName(ISSUER_NAME)
                .type(type)
                .issuedAt(certificate.getIssuedAt())
                .revokedAt(certificate.getRevokedAt())
                .verificationStatus(
                        certificate.getRevokedAt() == null
                                ? VERIFICATION_STATUS_VALID
                                : VERIFICATION_STATUS_REVOKED
                )
                .completionStatement(COMPLETION_STATEMENT)
                .disclaimer(DISCLAIMER)
                .platformProof(platformProof)
                .proofVerified(proofVerified)
                .build();
    }

    private String resolveRecipientName(User user) {
        if (user == null) {
            return "Học viên Skillverse";
        }

        String profileFullName = userProfileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFullName())
                .orElse(null);

        return sanitizeDisplayName(
                firstNonBlank(profileFullName, user.getFullName()),
                user.getEmail(),
                "Học viên Skillverse"
        );
    }

    private String resolveCourseTitle(Course course) {
        return course != null ? course.getTitle() : null;
    }

    private String resolveInstructorName(Course course) {
        if (course == null || course.getAuthor() == null) {
            return "Giảng viên Skillverse";
        }

        String mentorProfileFullName = mentorProfileRepository.findByUserId(course.getAuthor().getId())
                .map(profile -> profile.getFullName())
                .orElse(null);

        return sanitizeDisplayName(
                firstNonBlank(mentorProfileFullName, course.getAuthor().getFullName()),
                course.getAuthor().getEmail(),
                "Giảng viên Skillverse"
        );
    }

    private String resolveInstructorSignatureUrl(Course course) {
        if (course == null || course.getAuthor() == null) {
            return null;
        }

        return mentorProfileRepository.findByUserId(course.getAuthor().getId())
                .map(profile -> {
                    return sanitizeSignatureUrl(profile.getSignatureUrl());
                })
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateDTO> findUserCourseCertificate(Long courseId, Long userId) {
        return certificateRepository.findFirstByUser_IdAndCourse_IdOrderByIssuedAtDesc(userId, courseId)
                .map(this::buildCertificateDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateDTO> findActiveUserCourseCertificate(Long courseId, Long userId) {
        return certificateRepository.findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(
                        userId,
                        courseId
                )
                .map(this::buildCertificateDto);
    }

    private CertificateDTO buildCertificateDto(Certificate certificate) {
        String courseTitle = certificate.getCourseTitleSnapshot() != null
                ? certificate.getCourseTitleSnapshot()
                : (certificate.getCourse() != null ? certificate.getCourse().getTitle() : null);
        String platformProof = extractStoredPlatformProof(certificate.getCriteria());
        Boolean proofVerified = verifyPlatformProof(certificate, platformProof);

        return new CertificateDTO(
                certificate.getId(),
                certificate.getCourse() != null ? certificate.getCourse().getId() : null,
                certificate.getUser() != null ? certificate.getUser().getId() : null,
                courseTitle,
                resolveRecipientDisplayName(certificate),
                resolveInstructorDisplayName(certificate),
                resolveCertificateInstructorSignatureUrl(certificate),
                ISSUER_NAME,
                certificate.getType() != null ? certificate.getType().name() : null,
                certificate.getSerial(),
                certificate.getIssuedAt(),
                certificate.getRevokedAt(),
                certificate.getCriteria(),
                platformProof,
                proofVerified
        );
    }

    private String resolveRecipientDisplayName(Certificate certificate) {
        User user = certificate.getUser();
        String currentFullName = null;
        String email = null;
        if (user != null) {
            currentFullName = userProfileRepository.findByUserId(user.getId())
                    .map(profile -> profile.getFullName())
                    .orElseGet(user::getFullName);
            email = user.getEmail();
        }

        return selectPreferredDisplayName(
                certificate.getRecipientNameSnapshot(),
                currentFullName,
                email,
                "Học viên Skillverse"
        );
    }

    private String resolveInstructorDisplayName(Certificate certificate) {
        User author = certificate.getCourse() != null ? certificate.getCourse().getAuthor() : null;
        String currentFullName = null;
        String email = null;
        if (author != null) {
            currentFullName = mentorProfileRepository.findByUserId(author.getId())
                    .map(profile -> profile.getFullName())
                    .orElseGet(author::getFullName);
            email = author.getEmail();
        }

        return selectPreferredDisplayName(
                certificate.getInstructorNameSnapshot(),
                currentFullName,
                email,
                "Giảng viên Skillverse"
        );
    }

    private String resolveCertificateInstructorSignatureUrl(Certificate certificate) {
        String snapshot = sanitizeSignatureUrl(certificate.getInstructorSignatureUrlSnapshot());
        if (snapshot != null) {
            return snapshot;
        }

        if (certificate.getCourse() == null || certificate.getCourse().getAuthor() == null) {
            return null;
        }

        return mentorProfileRepository.findByUserId(certificate.getCourse().getAuthor().getId())
                .map(profile -> {
                    return sanitizeSignatureUrl(profile.getSignatureUrl());
                })
                .orElse(null);
    }

    private String sanitizeSignatureUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        String normalized = rawUrl.trim()
                .replace("://res cloudinary com/", "://res.cloudinary.com/")
                .replace("://res%20cloudinary%20com/", "://res.cloudinary.com/");

        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return null;
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }

            return normalized;
        } catch (URISyntaxException exception) {
            log.warn("Ignoring invalid instructor signature URL on certificate flow: {}", normalized);
            return null;
        }
    }

    private String selectPreferredDisplayName(
            String snapshot,
            String currentFullName,
            String email,
            String defaultValue
    ) {
        String snapshotDisplay = snapshot != null
                ? sanitizeDisplayName(snapshot, null, defaultValue)
                : null;
        String currentDisplay = sanitizeDisplayName(currentFullName, email, defaultValue);
        String emailDisplay = sanitizeEmailLocalPart(email);

        if (snapshotDisplay == null || snapshotDisplay.equals(defaultValue)) {
            return currentDisplay;
        }

        if (currentDisplay == null || currentDisplay.equals(defaultValue)) {
            return snapshotDisplay;
        }

        if (emailDisplay != null && equalsNormalized(snapshotDisplay, emailDisplay)) {
            return currentDisplay;
        }

        if (hasMultipleWords(currentDisplay) && !hasMultipleWords(snapshotDisplay)) {
            return currentDisplay;
        }

        return snapshotDisplay;
    }

    private String buildCriteriaSnapshot(
            Long courseId,
            Long userId,
            CourseLearningStatusDTO completionStatus,
            String serial,
            Instant issuedAt
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rule", "ALL_LESSONS_AND_REQUIRED_ASSESSMENTS_PASSED");
        snapshot.put("courseId", courseId);
        snapshot.put("userId", userId);
        snapshot.put("completedLessonCount", completionStatus.getCompletedLessonCount());
        snapshot.put("totalLessonCount", completionStatus.getTotalLessonCount());
        snapshot.put("completedQuizCount", completionStatus.getCompletedQuizCount());
        snapshot.put("totalQuizCount", completionStatus.getTotalQuizCount());
        snapshot.put(
                "completedRequiredAssignmentCount",
                completionStatus.getCompletedRequiredAssignmentCount()
        );
        snapshot.put(
                "totalRequiredAssignmentCount",
                completionStatus.getTotalRequiredAssignmentCount()
        );
        snapshot.put("completedItemCount", completionStatus.getCompletedItemCount());
        snapshot.put("totalItemCount", completionStatus.getTotalItemCount());
        snapshot.put("percent", completionStatus.getPercent());
        snapshot.put("issuedAt", issuedAt);
        snapshot.put(PLATFORM_PROOF_FIELD, computePlatformProof(serial, courseId, userId, issuedAt));

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CERTIFICATE_CRITERIA_SERIALIZATION_FAILED", exception);
        }
    }

    private String computePlatformProof(String serial, Long courseId, Long userId, Instant issuedAt) {
        try {
            String payload = String.join(
                    "|",
                    serial == null ? "" : serial,
                    courseId == null ? "" : String.valueOf(courseId),
                    userId == null ? "" : String.valueOf(userId),
                    issuedAt == null ? "" : issuedAt.toString()
            );
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    certificateProofSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKey);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("CERTIFICATE_PROOF_GENERATION_FAILED", exception);
        }
    }

    private String extractStoredPlatformProof(String criteriaJson) {
        if (criteriaJson == null || criteriaJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(criteriaJson);
            JsonNode proofNode = root.path(PLATFORM_PROOF_FIELD);
            if (proofNode.isMissingNode() || proofNode.isNull()) {
                return null;
            }

            String proofValue = proofNode.asText(null);
            return proofValue == null || proofValue.isBlank() ? null : proofValue.trim();
        } catch (Exception exception) {
            log.warn("Failed to parse certificate proof for serial validation");
            return null;
        }
    }

    private Boolean verifyPlatformProof(Certificate certificate, String storedProof) {
        if (certificate == null || storedProof == null) {
            return null;
        }

        Long courseId = certificate.getCourse() != null ? certificate.getCourse().getId() : null;
        Long userId = certificate.getUser() != null ? certificate.getUser().getId() : null;
        String expectedProof = computePlatformProof(
                certificate.getSerial(),
                courseId,
                userId,
                certificate.getIssuedAt()
        );
        return storedProof.equals(expectedProof);
    }

    private String generateSerial() {
        StringBuilder builder = new StringBuilder(SERIAL_PREFIX);
        for (int index = 0; index < SERIAL_RANDOM_LENGTH; index++) {
            int charIndex = SECURE_RANDOM.nextInt(SERIAL_ALPHABET.length());
            builder.append(SERIAL_ALPHABET.charAt(charIndex));
        }
        return builder.toString();
    }

    private String sanitizeDisplayName(String preferredValue, String fallbackEmail, String defaultValue) {
        String candidate = preferredValue != null && !preferredValue.isBlank() ? preferredValue.trim() : null;
        if (candidate != null && !candidate.contains("@")) {
            return candidate;
        }

        String emailSource = candidate != null && candidate.contains("@") ? candidate : fallbackEmail;
        String emailLocalPart = sanitizeEmailLocalPart(emailSource);
        if (emailLocalPart != null) {
            return emailLocalPart;
        }

        return defaultValue;
    }

    private String sanitizeEmailLocalPart(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String normalized = localPart.replaceAll("[._-]+", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        String[] parts = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasMultipleWords(String value) {
        return value != null && value.trim().contains(" ");
    }

    private boolean equalsNormalized(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().replaceAll("\\s+", " ").equalsIgnoreCase(
                right.trim().replaceAll("\\s+", " ")
        );
    }
}
