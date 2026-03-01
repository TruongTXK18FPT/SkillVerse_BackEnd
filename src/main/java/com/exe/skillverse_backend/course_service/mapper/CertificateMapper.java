package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.certificatedto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import java.time.Instant;
import java.util.UUID;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        config = CustomMapperConfig.class,
        uses = {UserMapper.class, CourseMapper.class},
        imports = {Instant.class}
)
public interface CertificateMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(
            target = "courseTitle",
            expression = "java(certificate.getCourseTitleSnapshot() != null ? certificate.getCourseTitleSnapshot() : (certificate.getCourse() != null ? certificate.getCourse().getTitle() : null))"
    )
    @Mapping(
            target = "recipientName",
            expression = "java(resolveDisplayName(certificate.getRecipientNameSnapshot(), certificate.getUser() != null ? certificate.getUser().getFullName() : null, certificate.getUser() != null ? certificate.getUser().getEmail() : null, \"Học viên Skillverse\"))"
    )
    @Mapping(
            target = "instructorName",
            expression = "java(resolveDisplayName(certificate.getInstructorNameSnapshot(), certificate.getCourse() != null && certificate.getCourse().getAuthor() != null ? certificate.getCourse().getAuthor().getFullName() : null, certificate.getCourse() != null && certificate.getCourse().getAuthor() != null ? certificate.getCourse().getAuthor().getEmail() : null, \"Giảng viên Skillverse\"))"
    )
    @Mapping(target = "instructorSignatureUrl", source = "instructorSignatureUrlSnapshot")
    @Mapping(target = "issuerName", constant = "Skillverse")
    @Mapping(
            target = "type",
            expression = "java(certificate.getType() != null ? certificate.getType().name() : null)"
    )
    @Mapping(target = "serial", source = "serial")
    @Mapping(target = "issuedAt", source = "issuedAt")
    @Mapping(target = "revokedAt", source = "revokedAt")
    @Mapping(target = "criteria", source = "criteria")
    CertificateDTO toDto(Certificate certificate);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "course", source = "course")
    @Mapping(target = "type", constant = "COURSE")
    @Mapping(target = "serial", source = "serial")
    @Mapping(target = "recipientNameSnapshot", source = "recipientNameSnapshot")
    @Mapping(target = "courseTitleSnapshot", source = "courseTitleSnapshot")
    @Mapping(target = "instructorNameSnapshot", source = "instructorNameSnapshot")
    @Mapping(target = "instructorSignatureUrlSnapshot", source = "instructorSignatureUrlSnapshot")
    @Mapping(target = "issuedAt", ignore = true)
    @Mapping(target = "revokedAt", ignore = true)
    @Mapping(target = "criteria", source = "criteria")
    @Mapping(target = "revokeReason", ignore = true)
    Certificate toEntity(
            CertificateIssueRequestDTO issueRequest,
            User user,
            Course course,
            String serial,
            String recipientNameSnapshot,
            String courseTitleSnapshot,
            String instructorNameSnapshot,
            String instructorSignatureUrlSnapshot,
            String criteria
    );

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "serial", ignore = true)
    @Mapping(target = "recipientNameSnapshot", ignore = true)
    @Mapping(target = "courseTitleSnapshot", ignore = true)
    @Mapping(target = "instructorNameSnapshot", ignore = true)
    @Mapping(target = "instructorSignatureUrlSnapshot", ignore = true)
    @Mapping(target = "issuedAt", ignore = true)
    @Mapping(target = "criteria", ignore = true)
    @Mapping(target = "revokedAt", expression = "java(Instant.now())")
    @Mapping(target = "revokeReason", source = "revokeReason")
    void revokeCertificate(@MappingTarget Certificate certificate, String revokeReason);

    // Helper method to generate unique serial number
    @Named("generateSerial")
    default String generateSerial() {
        return "CERT-"
                + System.currentTimeMillis()
                + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    default String resolveDisplayName(
            String snapshot,
            String fullName,
            String email,
            String defaultValue
    ) {
        String candidate = firstNonBlank(snapshot, fullName);
        if (candidate != null) {
            return sanitizeDisplayName(candidate, defaultValue);
        }

        String sanitizedEmail = sanitizeEmailLocalPart(email);
        return sanitizedEmail != null ? sanitizedEmail : defaultValue;
    }

    default String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    default String sanitizeDisplayName(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        String trimmed = value.trim();
        if (trimmed.contains("@")) {
            String sanitizedEmail = sanitizeEmailLocalPart(trimmed);
            return sanitizedEmail != null ? sanitizedEmail : defaultValue;
        }

        return trimmed;
    }

    default String sanitizeEmailLocalPart(String email) {
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
}
