package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.lessondto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

//@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface LessonProgressMapper {

    @Mapping(target = "lessonId", source = "lesson.id")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "timeSpenSec", source = "timeSpentSec")  // Note: DTO has typo "timeSpenSec"
    @Mapping(target = "lastPositionSec", source = "lastPositionSec")
    @Mapping(target = "updatedAt", source = "updatedAt")
    LessonProgressDetailDTO toDetailDto(LessonProgress progress);

    @Mapping(target = "id.userId", source = "user.id")
    @Mapping(target = "id.lessonId", source = "lesson.id")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "lesson", source = "lesson")
    @Mapping(target = "status", constant = "IN_PROGRESS")
    @Mapping(target = "timeSpentSec", constant = "0")
    @Mapping(target = "lastPositionSec", ignore = true)
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    LessonProgress toEntity(User user, Lesson lesson);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "lesson", ignore = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "timeSpentSec", source = "timeSpenSec")  // Note: DTO has typo
    @Mapping(target = "lastPositionSec", source = "lastPositionSec")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    void updateProgress(@MappingTarget LessonProgress progress, LessonProgressUpdateDTO updateDto);

    // Helper methods omitted since LessonProgressId is package-private
    // Use repository or service layer to handle entity creation
}