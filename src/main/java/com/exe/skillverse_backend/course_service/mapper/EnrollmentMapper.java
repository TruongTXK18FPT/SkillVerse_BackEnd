package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import java.time.Instant;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

//@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface EnrollmentMapper {

    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "progressPercent", source = "progressPercent")
    @Mapping(target = "entitlementSource", source = "entitlementSource")
    @Mapping(target = "entitlementRef", source = "entitlementRef")
    EnrollmentDTO toDto(CourseEnrollment enrollment);

    @Mapping(target = "id.userId", source = "user.id")
    @Mapping(target = "id.courseId", source = "course.id")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "course", source = "course")
    @Mapping(target = "enrollDate", expression = "java(Instant.now())")
    @Mapping(target = "status", constant = "ENROLLED")
    @Mapping(target = "progressPercent", constant = "0")
    @Mapping(target = "entitlementSource", source = "entitlementSource")
    @Mapping(target = "entitlementRef", source = "entitlementRef")
    CourseEnrollment toEntity(EnrollRequestDTO enrollRequest, User user, Course course, 
                             String entitlementSource, String entitlementRef);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "enrollDate", ignore = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "progressPercent", source = "progressPercent")
    @Mapping(target = "entitlementSource", ignore = true)
    @Mapping(target = "entitlementRef", ignore = true)
    void updateEnrollment(@MappingTarget CourseEnrollment enrollment, EnrollmentDTO enrollmentDto);

    // Helper methods omitted since CourseEnrollmentId is package-private
    // Use repository or service layer to handle entity creation
}
