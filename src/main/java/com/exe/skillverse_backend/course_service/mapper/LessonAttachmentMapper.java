package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;
import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface LessonAttachmentMapper {

    @Mapping(target = "downloadUrl", source = "downloadUrl")
    @Mapping(target = "fileSizeFormatted", source = "formattedFileSize")
    LessonAttachmentDTO toDto(LessonAttachment attachment);

    @Named("mapDownloadUrl")
    default String mapDownloadUrl(LessonAttachment attachment) {
        return attachment.getDownloadUrl();
    }

    @Named("mapFormattedFileSize")
    default String mapFormattedFileSize(LessonAttachment attachment) {
        return attachment.getFormattedFileSize();
    }
}