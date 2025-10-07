package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.moduledto.*;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = { LessonMapper.class })
public interface ModuleMapper {

  @Mapping(target = "id", source = "id")
  @Mapping(target = "title", source = "title")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "orderIndex", source = "orderIndex")
  ModuleSummaryDTO toSummaryDto(Module module);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "title", source = "title")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "orderIndex", source = "orderIndex")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "updatedAt", source = "updatedAt")
  @Mapping(target = "lessons", source = "lessons")
  ModuleDetailDTO toDetailDto(Module module);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "course", source = "course")
  @Mapping(target = "title", source = "dto.title")
  @Mapping(target = "description", source = "dto.description")
  @Mapping(target = "orderIndex", source = "dto.orderIndex")
  @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
  @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
  @Mapping(target = "lessons", ignore = true)
  Module toEntity(ModuleCreateDTO dto, Course course);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "course", ignore = true)
  @Mapping(target = "title", source = "dto.title")
  @Mapping(target = "description", source = "dto.description")
  @Mapping(target = "orderIndex", source = "dto.orderIndex")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
  @Mapping(target = "lessons", ignore = true)
  void updateEntity(@MappingTarget Module module, ModuleUpdateDTO dto);
}


