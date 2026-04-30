package com.exe.skillverse_backend.shared.mapper;

import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.dto.MediaCreateDTO;
import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.entity.Media;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface MediaMapper {

    // Entity -> DTO
    @Mapping(target = "uploadedByName", expression = "java(media.getUploadedByUser() != null ? (media.getUploadedByUser().getFirstName() + \" \" + media.getUploadedByUser().getLastName()).trim() : null)")
    @Mapping(target = "cloudinaryPublicId", source = "cloudinaryPublicId")
    MediaDTO toDto(Media media);

    // DTO (create) -> Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uploadedByUser", ignore = true) // set bằng uploadedBy (FK) là đủ
    @Mapping(target = "uploadedAt", expression = "java(java.time.LocalDateTime.now())")
    Media toEntity(MediaCreateDTO dto);

    // Update entity từ DTO (nếu bạn có DTO update)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Media target, MediaDTO source);
}

