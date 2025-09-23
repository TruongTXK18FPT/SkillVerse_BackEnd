package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.codingdto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = {CodingTestCaseMapper.class})
public interface CodingExerciseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "prompt", source = "prompt")
    @Mapping(target = "language", source = "language")
    @Mapping(target = "startedCode", source = "starterCode")
    @Mapping(target = "maxScore", source = "maxScore")
    @Mapping(target = "testCases", source = "testCases")
    CodingExerciseDetailDTO toDetailDto(CodingExercise exercise);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "prompt", source = "createDto.prompt")
    @Mapping(target = "language", source = "createDto.language")
    @Mapping(target = "starterCode", source = "createDto.startedCode")
    @Mapping(target = "maxScore", source = "createDto.maxScore")
    @Mapping(target = "lesson", source = "lesson")
    @Mapping(target = "testCases", ignore = true)
    CodingExercise toEntity(CodingExerciseCreateDTO createDto, Lesson lesson);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "prompt", source = "updateDto.prompt")
    @Mapping(target = "language", source = "updateDto.language")
    @Mapping(target = "starterCode", source = "updateDto.startedCode")
    @Mapping(target = "maxScore", source = "updateDto.maxScore")
    @Mapping(target = "lesson", ignore = true)
    @Mapping(target = "testCases", ignore = true)
    void updateEntity(@MappingTarget CodingExercise exercise, CodingExerciseUpdateDTO updateDto);
}