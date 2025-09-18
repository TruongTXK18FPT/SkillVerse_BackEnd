package com.exe.skillverse_backend.shared.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for creating secure audit log entries
 * Ensures PII and sensitive data are not logged
 */
@Slf4j
public class SecureAuditUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{10,15}");

    static {
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Create secure audit details for user registration
     */
    public static String createRegistrationAuditDetails(String email, String userRole) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "USER_REGISTRATION");
        details.put("email", maskEmail(email));
        details.put("role", userRole);
        details.put("timestamp", System.currentTimeMillis());

        return toJsonString(details);
    }

    /**
     * Create secure audit details for profile updates
     */
    public static String createProfileUpdateAuditDetails(String email, String[] updatedFields) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "PROFILE_UPDATE");
        details.put("email", maskEmail(email));
        details.put("updatedFields", updatedFields); // Only field names, not values
        details.put("timestamp", System.currentTimeMillis());

        return toJsonString(details);
    }

    /**
     * Create secure audit details for authentication events
     */
    public static String createAuthAuditDetails(String email, String action, boolean success, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", action);
        details.put("email", maskEmail(email));
        details.put("success", success);
        details.put("ipAddress", maskIpAddress(ipAddress));
        details.put("timestamp", System.currentTimeMillis());

        return toJsonString(details);
    }

    /**
     * Create secure audit details for admin actions
     */
    public static String createAdminActionAuditDetails(String adminEmail, String action, String targetEmail,
            String objectType) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", action);
        details.put("adminEmail", maskEmail(adminEmail));
        details.put("targetEmail", targetEmail != null ? maskEmail(targetEmail) : null);
        details.put("objectType", objectType);
        details.put("timestamp", System.currentTimeMillis());

        return toJsonString(details);
    }

    /**
     * Create secure audit details for data access
     */
    public static String createDataAccessAuditDetails(String userEmail, String action, String resourceType,
            String resourceId) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", action);
        details.put("userEmail", maskEmail(userEmail));
        details.put("resourceType", resourceType);
        details.put("resourceId", resourceId);
        details.put("timestamp", System.currentTimeMillis());

        return toJsonString(details);
    }

    /**
     * Mask email address for audit logging
     * Example: john.doe@example.com -> j***e@e***e.com
     */
    private static String maskEmail(String email) {
        if (email == null || email.length() < 3) {
            return "[MASKED]";
        }

        String[] parts = email.split("@");
        if (parts.length != 2) {
            return "[MASKED]";
        }

        String localPart = parts[0];
        String domainPart = parts[1];

        // Mask local part
        String maskedLocal = localPart.length() > 2
                ? localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1)
                : "***";

        // Mask domain part
        String maskedDomain = domainPart.length() > 2
                ? domainPart.charAt(0) + "***" + domainPart.substring(domainPart.lastIndexOf('.'))
                : "***";

        return maskedLocal + "@" + maskedDomain;
    }

    /**
     * Mask IP address for audit logging
     * Example: 192.168.1.100 -> 192.168.***.***
     */
    private static String maskIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }

        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }

        // For IPv6 or other formats, just mask the end
        return ipAddress.length() > 8
                ? ipAddress.substring(0, 8) + "***"
                : "***";
    }

    /**
     * Sanitize any string to remove PII
     */
    public static String sanitizeString(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input;

        // Mask emails
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(m -> maskEmail(m.group()));

        // Mask phone numbers
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("***PHONE***");

        // Remove common PII patterns
        sanitized = sanitized.replaceAll("(?i)(password|pwd|pass)\\s*[:=]\\s*\\S+", "$1:***");
        sanitized = sanitized.replaceAll("(?i)(ssn|social\\s*security)\\s*[:=]\\s*\\S+", "$1:***");
        sanitized = sanitized.replaceAll("(?i)(credit\\s*card|cc)\\s*[:=]\\s*\\S+", "$1:***");

        return sanitized;
    }

    /**
     * Convert object to JSON string safely
     */
    private static String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert audit details to JSON", e);
            return "{\"error\":\"Failed to serialize audit details\"}";
        }
    }

    /**
     * Check if a field should be audited (not sensitive)
     */
    public static boolean isSafeToAudit(String fieldName, Object value) {
        if (fieldName == null || value == null) {
            return false;
        }

        String lowerFieldName = fieldName.toLowerCase();

        // Sensitive fields that should never be audited
        return !lowerFieldName.contains("password") &&
                !lowerFieldName.contains("ssn") &&
                !lowerFieldName.contains("social") &&
                !lowerFieldName.contains("credit") &&
                !lowerFieldName.contains("card") &&
                !lowerFieldName.contains("secret") &&
                !lowerFieldName.contains("token") &&
                !lowerFieldName.contains("key");
    }
}