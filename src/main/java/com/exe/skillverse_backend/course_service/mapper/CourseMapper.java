package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.coursedto.*;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = {UserMapper.class, MediaMapper.class, LessonMapper.class})
public interface CourseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "level", source = "level")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "thumbnail", source = "thumbnail")
    @Mapping(target = "lessons", source = "lessons")
    CourseDetailDTO toDetailDto(Course course);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "level", source = "level")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "authorName", expression = "java((course.getAuthor().getFirstName() + \" \" + course.getAuthor().getLastName()).trim())")
    @Mapping(target = "thumbnailMediaId", source = "thumbnail.id")
    @Mapping(target = "enrollmentCount", expression = "java(course.getEnrollments() != null ? course.getEnrollments().size() : 0)")
    CourseSummaryDTO toSummaryDto(Course course);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "description", source = "createDto.description")
    @Mapping(target = "level", source = "createDto.level")
    @Mapping(target = "status", constant = "PUBLIC")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "thumbnail", source = "thumbnail")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lessons", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "purchases", ignore = true)
    @Mapping(target = "certificates", ignore = true)
    @Mapping(target = "courseSkills", ignore = true)
    Course toEntity(CourseCreateDTO createDto, User author, Media thumbnail);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "description", source = "updateDto.description")
    @Mapping(target = "level", source = "updateDto.level")
    @Mapping(target = "status", source = "updateDto.status")
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "thumbnail", source = "thumbnail")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lessons", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "purchases", ignore = true)
    @Mapping(target = "certificates", ignore = true)
    @Mapping(target = "courseSkills", ignore = true)
    void updateEntity(@MappingTarget Course course, CourseUpdateDTO updateDto, Media thumbnail);

    // Helper method to map from ID to Media entity (for cases where only ID is provided)
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
        if (mediaId == null) return null;
        return mapIdToMedia(mediaId);
    }
}