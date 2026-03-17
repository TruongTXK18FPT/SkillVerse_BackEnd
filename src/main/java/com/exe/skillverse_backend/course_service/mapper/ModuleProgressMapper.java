package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleProgressDetailDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleProgressUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.ModuleProgress;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import java.time.Instant;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, imports = {Instant.class})
public interface ModuleProgressMapper {

    @Mapping(target = "moduleId", source = "module.id")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "timeSpentSec", source = "timeSpentSec")
    @Mapping(target = "lastPositionSec", source = "lastPositionSec")
    @Mapping(target = "updatedAt", source = "updatedAt")
    ModuleProgressDetailDTO toDetailDto(ModuleProgress progress);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "status", constant = "IN_PROGRESS")
    @Mapping(target = "timeSpentSec", constant = "0")
    @Mapping(target = "lastPositionSec", ignore = true)
    @Mapping(target = "updatedAt", expression = "java(Instant.now())")
    ModuleProgress toEntity(User user, Module module);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "timeSpentSec", source = "timeSpentSec")
    @Mapping(target = "lastPositionSec", source = "lastPositionSec")
    @Mapping(target = "updatedAt", expression = "java(Instant.now())")
    void updateProgress(@MappingTarget ModuleProgress progress, ModuleProgressUpdateDTO updateDto);

    // Helper methods omitted since ModuleProgressId is package-private
    // Use repository or service layer to handle entity creation
}
