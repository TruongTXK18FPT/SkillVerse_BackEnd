package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.lessondto.*;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class, uses = {MediaMapper.class})
public interface LessonMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "durationSec", source = "durationSec")
    LessonBriefDTO toBriefDto(Lesson lesson);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "contentText", source = "contentText")
    @Mapping(target = "videoUrl", source = "videoUrl")
    @Mapping(target = "videoMedia", source = "videoMedia")
    @Mapping(target = "durationSec", source = "durationSec")
    @Mapping(target = "course", source = "course")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "quiz", ignore = true)
    @Mapping(target = "assignment", ignore = true)
    @Mapping(target = "codelab", ignore = true)
    Lesson toEntity(LessonCreateDTO createDto, Course course, Media videoMedia);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "contentText", source = "contentText")
    @Mapping(target = "videoUrl", source = "videoUrl")
    @Mapping(target = "videoMedia", source = "videoMedia")
    @Mapping(target = "durationSec", source = "durationSec")
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "quiz", ignore = true)
    @Mapping(target = "assignment", ignore = true)
    @Mapping(target = "codelab", ignore = true)
    void updateEntity(@MappingTarget Lesson lesson, LessonUpdateDTO updateDto, Media videoMedia);

    // Helper method to map from ID to Media entity
    @Mapping(target = "id", source = "videoMediaId")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "fileName", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "uploadedBy", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Media mapIdToMedia(Long videoMediaId);

    @Named("mapMediaIdToEntity")
    default Media mapMediaIdToEntity(Long mediaId) {
        if (mediaId == null) return null;
        return mapIdToMedia(mediaId);
    }
}