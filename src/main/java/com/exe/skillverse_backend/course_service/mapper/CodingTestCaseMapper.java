package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.codingdto.CodingTestCaseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingTestCaseDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingTestCaseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.CodingExercise;
import com.exe.skillverse_backend.course_service.entity.CodingTestCase;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface CodingTestCaseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "scoreWeight", source = "scoreWeight")
    @Mapping(target = "orderIndex", source = "orderIndex")
    CodingTestCaseDTO toDto(CodingTestCase testCase);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "kind", source = "createDto.kind")
    @Mapping(target = "input", source = "createDto.input")
    @Mapping(target = "expectedOutput", source = "createDto.expectedOutput")
    @Mapping(target = "scoreWeight", source = "createDto.scoreWeight")
    @Mapping(target = "orderIndex", source = "createDto.orderIndex")
    @Mapping(target = "exercise", source = "exercise")
    CodingTestCase toEntity(CodingTestCaseCreateDTO createDto, CodingExercise exercise);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "kind", source = "updateDto.kind")
    @Mapping(target = "input", source = "updateDto.input")
    @Mapping(target = "expectedOutput", source = "updateDto.expectedOutput")
    @Mapping(target = "scoreWeight", source = "updateDto.scoreWeight")
    @Mapping(target = "orderIndex", source = "updateDto.orderIndex")
    @Mapping(target = "exercise", ignore = true)
    void updateEntity(@MappingTarget CodingTestCase testCase, CodingTestCaseUpdateDTO updateDto);
}
