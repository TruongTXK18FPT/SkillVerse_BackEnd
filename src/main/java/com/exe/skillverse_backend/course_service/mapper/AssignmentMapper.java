package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = {UserMapper.class, MediaMapper.class})
public interface AssignmentMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "submissionType", source = "submissionType")
    @Mapping(target = "maxScore", source = "maxScore")
    @Mapping(target = "dueAt", source = "dueAt")
    @Mapping(target = "description", source = "description")
    AssignmentDetailDTO toDetailDto(Assignment assignment);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "submissionType", source = "submissionType")
    @Mapping(target = "maxScore", source = "maxScore")
    @Mapping(target = "dueAt", source = "dueAt")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "moduleId", source = "module.id")
    AssignmentSummaryDTO toSummaryDto(Assignment assignment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "description", source = "createDto.description")
    @Mapping(target = "submissionType", source = "createDto.submissionType")
    @Mapping(target = "maxScore", source = "createDto.maxScore")
    @Mapping(target = "dueAt", source = "createDto.dueAt")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "submissions", ignore = true)
    Assignment toEntity(AssignmentCreateDTO createDto, Module module);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "description", source = "updateDto.description")
    @Mapping(target = "submissionType", source = "updateDto.submissionType")
    @Mapping(target = "maxScore", source = "updateDto.maxScore")
    @Mapping(target = "dueAt", source = "updateDto.dueAt")
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "submissions", ignore = true)
    void updateEntity(@MappingTarget Assignment assignment, AssignmentUpdateDTO updateDto);
}