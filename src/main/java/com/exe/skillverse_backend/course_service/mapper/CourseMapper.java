package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(config = CustomMapperConfig.class, uses = { UserMapper.class, MediaMapper.class, ModuleMapper.class })
public interface CourseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "shortDescription", source = "shortDescription")
    @Mapping(target = "level", source = "level")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "estimatedDurationHours", source = "estimatedDurationHours")
    @Mapping(target = "language", source = "language")
    @Mapping(target = "learningObjectives", source = "learningObjectives")
    @Mapping(target = "requirements", source = "requirements")
    @Mapping(target = "courseSkills", source = "courseSkillTags")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "thumbnail", source = "thumbnail")
    @Mapping(target = "modules", source = "modules")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "authorName", expression = "java(getAuthorFullName(course))")
    @Mapping(target = "thumbnailUrl", source = "thumbnail.url")
    @Mapping(target = "enrollmentCount", expression = "java(getEnrollmentCount(course))")
    @Mapping(target = "submittedDate", expression = "java(toLocalDateTime(course.getSubmittedAt()))")
    @Mapping(target = "publishedDate", expression = "java(toLocalDateTime(course.getPublishedAt()))")
    @Mapping(target = "createdAt", expression = "java(toLocalDateTime(course.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toLocalDateTime(course.getUpdatedAt()))")
    @Mapping(target = "rejectionReason", source = "rejectionReason")
    @Mapping(target = "rejectedAt", expression = "java(toLocalDateTime(course.getRejectedAt()))")
    @Mapping(target = "suspensionReason", source = "suspensionReason")
    @Mapping(target = "suspendedAt", expression = "java(toLocalDateTime(course.getSuspendedAt()))")
    CourseDetailDTO toDetailDto(Course course);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "shortDescription", source = "shortDescription")
    @Mapping(target = "level", source = "level")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "estimatedDurationHours", source = "estimatedDurationHours")
    @Mapping(target = "language", source = "language")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "authorName", expression = "java(getAuthorFullName(course))")
    @Mapping(target = "thumbnailMediaId", source = "thumbnail.id")
    @Mapping(target = "thumbnailUrl", source = "thumbnail.url")
    @Mapping(target = "enrollmentCount", expression = "java(getEnrollmentCount(course))")
    @Mapping(target = "moduleCount", expression = "java(getModuleCount(course))")
    @Mapping(target = "lessonCount", expression = "java(getLessonCount(course))")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "submittedDate", expression = "java(toLocalDateTime(course.getSubmittedAt()))")
    @Mapping(target = "publishedDate", expression = "java(toLocalDateTime(course.getPublishedAt()))")
    @Mapping(target = "createdAt", expression = "java(toLocalDateTime(course.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toLocalDateTime(course.getUpdatedAt()))")
    @Mapping(target = "rejectionReason", source = "rejectionReason")
    CourseSummaryDTO toSummaryDto(Course course);

    // Helper methods for safe null handling
    default String getAuthorFullName(Course course) {
        if (course == null || course.getAuthor() == null)
            return "Unknown";
        String firstName = course.getAuthor().getFirstName() != null ? course.getAuthor().getFirstName() : "";
        String lastName = course.getAuthor().getLastName() != null ? course.getAuthor().getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "Unknown" : fullName;
    }

    default Integer getEnrollmentCount(Course course) {
        if (course == null)
            return 0;
        try {
            return course.getEnrollments() != null ? course.getEnrollments().size() : 0;
        } catch (Exception e) {
            // Handle lazy initialization exception
            return 0;
        }
    }

    default Integer getModuleCount(Course course) {
        if (course == null)
            return 0;
        try {
            return course.getModules() != null ? course.getModules().size() : 0;
        } catch (Exception e) {
            // Handle lazy initialization exception
            return 0;
        }
    }

    default Integer getLessonCount(Course course) {
        if (course == null)
            return 0;
        try {
            if (course.getModules() == null)
                return 0;
            return course.getModules().stream()
                    .mapToInt(module -> {
                        int lessonCount = module.getLessons() != null ? module.getLessons().size() : 0;
                        int quizCount = module.getQuizzes() != null ? module.getQuizzes().size() : 0;
                        int assignmentCount = module.getAssignments() != null ? module.getAssignments().size() : 0;
                        return lessonCount + quizCount + assignmentCount;
                    })
                    .sum();
        } catch (Exception e) {
            // Handle lazy initialization exception
            return 0;
        }
    }

    // Date conversion helpers
    default LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null)
            return null;
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "description", source = "createDto.description")
    @Mapping(target = "shortDescription", source = "createDto.shortDescription")
    @Mapping(target = "level", source = "createDto.level")
    @Mapping(target = "category", source = "createDto.category")
    @Mapping(target = "estimatedDurationHours", source = "createDto.estimatedDurationHours")
    @Mapping(target = "language", source = "createDto.language")
    @Mapping(target = "learningObjectives", source = "createDto.learningObjectives")
    @Mapping(target = "requirements", source = "createDto.requirements")
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "thumbnail", source = "thumbnail")
    @Mapping(target = "price", source = "createDto.price")
    @Mapping(target = "currency", source = "createDto.currency")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "suspensionReason", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "suspendedBy", ignore = true)
    @Mapping(target = "modules", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "purchases", ignore = true)
    @Mapping(target = "certificates", ignore = true)
    @Mapping(target = "courseSkills", ignore = true)
    @Mapping(target = "courseSkillTags", source = "createDto.courseSkills")
    Course toEntity(CourseCreateDTO createDto, User author, Media thumbnail);

    @AfterMapping
    default void afterToEntity(CourseCreateDTO dto, @MappingTarget Course course) {
        if (course.getCourseSkillTags() == null) {
            course.setCourseSkillTags(new java.util.ArrayList<>());
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "description", source = "updateDto.description")
    @Mapping(target = "shortDescription", source = "updateDto.shortDescription")
    @Mapping(target = "level", source = "updateDto.level")
    @Mapping(target = "category", source = "updateDto.category")
    @Mapping(target = "estimatedDurationHours", source = "updateDto.estimatedDurationHours")
    @Mapping(target = "language", source = "updateDto.language")
    @Mapping(target = "learningObjectives", source = "updateDto.learningObjectives")
    @Mapping(target = "requirements", source = "updateDto.requirements")
    @Mapping(target = "status", ignore = true) // Don't update status through this endpoint
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "thumbnail", source = "thumbnail")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "suspensionReason", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "suspendedBy", ignore = true)
    @Mapping(target = "price", source = "updateDto.price")
    @Mapping(target = "currency", source = "updateDto.currency")
    @Mapping(target = "modules", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "purchases", ignore = true)
    @Mapping(target = "certificates", ignore = true)
    @Mapping(target = "courseSkills", ignore = true)
    @Mapping(target = "courseSkillTags", source = "updateDto.courseSkills")
    void updateEntity(@MappingTarget Course course, CourseUpdateDTO updateDto, Media thumbnail);

    @AfterMapping
    default void afterUpdateEntity(CourseUpdateDTO dto, @MappingTarget Course course) {
        if (dto.getCourseSkills() != null) {
            course.setCourseSkillTags(new java.util.ArrayList<>(dto.getCourseSkills()));
        } else if (course.getCourseSkillTags() == null) {
            course.setCourseSkillTags(new java.util.ArrayList<>());
        }
    }

    // Helper method to map from ID to Media entity (for cases where only ID is
    // provided)
    @Mapping(target = "id", source = "thumbnailMediaId")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "fileName", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "uploadedBy", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    Media mapIdToMedia(Long thumbnailMediaId);

    @Named("mapMediaIdToEntity")
    default Media mapMediaIdToEntity(Long mediaId) {
        if (mediaId == null)
            return null;
        return mapIdToMedia(mediaId);
    }
}
