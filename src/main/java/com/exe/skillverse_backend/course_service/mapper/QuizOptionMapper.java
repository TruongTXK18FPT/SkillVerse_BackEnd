package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class)
public interface QuizOptionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "optionText", source = "optionText")
    @Mapping(target = "isCorrect", source = "isCorrect")
    @Mapping(target = "feedback", source = "feedback")
    QuizOptionDetailDTO toDetailDto(QuizOption option);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "optionText", source = "optionText")
    @Mapping(target = "isCorrect", source = "isCorrect")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "question", source = "question")
    QuizOption toEntity(QuizOptionCreateDTO createDto, QuizQuestion question);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "optionText", source = "optionText")
    @Mapping(target = "isCorrect", source = "isCorrect")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "question", ignore = true)
    void updateEntity(@MappingTarget QuizOption option, QuizOptionUpdateDTO updateDto);
}