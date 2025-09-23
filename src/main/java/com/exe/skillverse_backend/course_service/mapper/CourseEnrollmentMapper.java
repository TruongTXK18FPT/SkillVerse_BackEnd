package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

//@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface CourseEnrollmentMapper {

    @Mapping(target = "id", source = "id.courseId")
    @Mapping(target = "courseId", source = "id.courseId")
    @Mapping(target = "courseTitle", source = "course.title")
    @Mapping(target = "courseSlug", source = "course.slug")
    @Mapping(target = "userId", source = "id.userId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "progressPercent", source = "progressPercent")
    @Mapping(target = "entitlementSource", source = "entitlementSource")
    @Mapping(target = "entitlementRef", source = "entitlementRef")
    @Mapping(target = "enrolledAt", source = "enrollDate")
    @Mapping(target = "completedAt", expression = "java(enrollment.getStatus() == com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus.COMPLETED ? enrollment.getEnrollDate() : null)")
    @Mapping(target = "completed", expression = "java(enrollment.getStatus() == com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus.COMPLETED)")
    EnrollmentDetailDTO toDetailDto(CourseEnrollment enrollment);
}