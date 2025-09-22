package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.certificatedto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class, uses = {UserMapper.class, CourseMapper.class})
public interface CertificateMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "serial", source = "serial")
    @Mapping(target = "issuedAt", source = "issuedAt")
    @Mapping(target = "revokedAt", source = "revokedAt")
    CertificateDTO toDto(Certificate certificate);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "course", source = "course")
    @Mapping(target = "type", constant = "COURSE")
    @Mapping(target = "serial", source = "serial")
    @Mapping(target = "issuedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "revokedAt", ignore = true)
    @Mapping(target = "criteria", source = "criteria")
    @Mapping(target = "revokeReason", ignore = true)
    Certificate toEntity(CertificateIssueRequestDTO issueRequest, User user, Course course, 
                        String serial, String criteria);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "serial", ignore = true)
    @Mapping(target = "issuedAt", ignore = true)
    @Mapping(target = "criteria", ignore = true)
    @Mapping(target = "revokedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "revokeReason", source = "revokeReason")
    void revokeCertificate(@MappingTarget Certificate certificate, String revokeReason);

    // Helper method to generate unique serial number
    @Named("generateSerial")
    default String generateSerial() {
        return "CERT-" + System.currentTimeMillis() + "-" + 
               java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}