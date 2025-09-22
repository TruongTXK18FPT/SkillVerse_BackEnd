package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class, uses = {QuizOptionMapper.class})
public interface QuizQuestionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "questionText", source = "questionText")
    @Mapping(target = "questionType", source = "questionType")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "options", source = "options")
    QuizQuestionDetailDTO toDetailDto(QuizQuestion question);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "questionText", source = "questionText")
    @Mapping(target = "questionType", source = "questionType")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "quiz", source = "quiz")
    @Mapping(target = "options", ignore = true)
    QuizQuestion toEntity(QuizQuestionCreateDTO createDto, Quiz quiz);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "questionText", source = "questionText")
    @Mapping(target = "questionType", source = "questionType")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "quiz", ignore = true)
    @Mapping(target = "options", ignore = true)
    void updateEntity(@MappingTarget QuizQuestion question, QuizQuestionUpdateDTO updateDto);
}