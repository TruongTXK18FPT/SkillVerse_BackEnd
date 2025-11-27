package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.lessondto.*;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = { MediaMapper.class })
public interface LessonMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "durationSec", source = "durationSec")
    @Mapping(target = "contentText", source = "contentText")
    @Mapping(target = "videoUrl", source = "videoUrl")
    @Mapping(target = "videoMediaId", source = "videoMedia.id")
    LessonBriefDTO toBriefDto(Lesson lesson);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "durationSec", source = "durationSec")
    @Mapping(target = "contentText", source = "contentText")
    @Mapping(target = "videoUrl", source = "videoUrl")
    @Mapping(target = "videoMediaId", source = "videoMedia.id")
    LessonDetailDTO toDetailDto(Lesson lesson);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "type", source = "createDto.type")
    @Mapping(target = "orderIndex", source = "createDto.orderIndex")
    @Mapping(target = "contentText", source = "createDto.contentText")
    @Mapping(target = "videoUrl", source = "createDto.videoUrl")
    @Mapping(target = "videoMedia", source = "videoMedia")
    @Mapping(target = "durationSec", source = "createDto.durationSec")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Lesson toEntity(LessonCreateDTO createDto, com.exe.skillverse_backend.course_service.entity.Module module,
            Media videoMedia);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "type", source = "updateDto.type")
    @Mapping(target = "orderIndex", source = "updateDto.orderIndex")
    @Mapping(target = "contentText", source = "updateDto.contentText")
    @Mapping(target = "videoUrl", source = "updateDto.videoUrl")
    @Mapping(target = "videoMedia", source = "videoMedia")
    @Mapping(target = "durationSec", source = "updateDto.durationSec")
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Lesson lesson, LessonUpdateDTO updateDto, Media videoMedia);

    // Helper method to map from ID to Media entity
    @Mapping(target = "id", source = "videoMediaId")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "fileName", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "uploadedBy", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    Media mapIdToMedia(Long videoMediaId);

    @Named("mapMediaIdToEntity")
    default Media mapMediaIdToEntity(Long mediaId) {
        if (mediaId == null)
            return null;
        return mapIdToMedia(mediaId);
    }
}