package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonDetailDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = { MediaMapper.class })
public interface LessonMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "durationSec", source = "durationSec")
    @Mapping(target = "contentText", source = "contentText")
    @Mapping(target = "resourceUrl", source = "resourceUrl")
    @Mapping(target = "videoUrl", source = ".", qualifiedByName = "resolveVideoUrl")
    @Mapping(target = "videoMediaId", source = "videoMedia.id")
    LessonBriefDTO toBriefDto(Lesson lesson);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "durationSec", source = "durationSec")
    @Mapping(target = "contentText", source = "contentText")
    @Mapping(target = "resourceUrl", source = "resourceUrl")
    @Mapping(target = "videoUrl", source = ".", qualifiedByName = "resolveVideoUrl")
    @Mapping(target = "videoMediaId", source = "videoMedia.id")
    LessonDetailDTO toDetailDto(Lesson lesson);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "type", source = "createDto.type")
    @Mapping(target = "orderIndex", source = "createDto.orderIndex")
    @Mapping(target = "contentText", source = "createDto.contentText")
    @Mapping(target = "resourceUrl", source = "createDto.resourceUrl")
    @Mapping(target = "videoUrl", source = "createDto.videoUrl")
    @Mapping(target = "videoMedia", source = "videoMedia")
    @Mapping(target = "durationSec", source = "createDto.durationSec")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Lesson toEntity(LessonCreateDTO createDto, Module module, Media videoMedia);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "type", source = "updateDto.type")
    @Mapping(target = "orderIndex", source = "updateDto.orderIndex")
    @Mapping(target = "contentText", source = "updateDto.contentText")
    @Mapping(target = "resourceUrl", source = "updateDto.resourceUrl")
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

    /**
     * Resolve video URL from either videoUrl field (YouTube) or videoMedia.url (uploaded file).
     * Priority: videoUrl field (external URLs like YouTube) > videoMedia.url (uploaded files)
     */
    @Named("resolveVideoUrl")
    default String resolveVideoUrl(Lesson lesson) {
        // Priority 1: External URL (YouTube, etc.) from videoUrl field
        if (lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank()) {
            return lesson.getVideoUrl();
        }
        // Priority 2: Uploaded video file URL from Media entity
        if (lesson.getVideoMedia() != null && lesson.getVideoMedia().getUrl() != null) {
            return lesson.getVideoMedia().getUrl();
        }
        return null;
    }
}
