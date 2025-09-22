package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.codingdto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class)
public interface CodingTestCaseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "input", source = "input")
    @Mapping(target = "expectedOutput", source = "expectedOutput")
    @Mapping(target = "scoreWeight", source = "scoreWeight")
    @Mapping(target = "orderIndex", source = "orderIndex")
    CodingTestCaseDTO toDto(CodingTestCase testCase);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "input", source = "input")
    @Mapping(target = "expectedOutput", source = "expectedOutput")
    @Mapping(target = "scoreWeight", source = "scoreWeight")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "exercise", source = "exercise")
    CodingTestCase toEntity(CodingTestCaseCreateDTO createDto, CodingExercise exercise);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "input", source = "input")
    @Mapping(target = "expectedOutput", source = "expectedOutput")
    @Mapping(target = "scoreWeight", source = "scoreWeight")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "exercise", ignore = true)
    void updateEntity(@MappingTarget CodingTestCase testCase, CodingTestCaseUpdateDTO updateDto);
}