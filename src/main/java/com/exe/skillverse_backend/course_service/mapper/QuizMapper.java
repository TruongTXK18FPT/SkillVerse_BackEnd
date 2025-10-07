package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = {QuizQuestionMapper.class})
public interface QuizMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "passScore", source = "passScore")
    @Mapping(target = "questions", source = "questions")
    QuizDetailDTO toDetailDto(Quiz quiz);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "passScore", source = "passScore")
    @Mapping(target = "questionCount", expression = "java(getQuestionCount(quiz))")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    QuizSummaryDTO toSummaryDto(Quiz quiz);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "description", source = "createDto.description")
    @Mapping(target = "passScore", source = "createDto.passScore")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "questions", ignore = true)
    Quiz toEntity(QuizCreateDTO createDto, Module module);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "passScore", source = "passScore")
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "questions", ignore = true)
    void updateEntity(@MappingTarget Quiz quiz, QuizUpdateDTO updateDto);
    
    // Helper method for safe question count calculation
    default Integer getQuestionCount(Quiz quiz) {
        if (quiz == null) return 0;
        try {
            return quiz.getQuestions() != null ? quiz.getQuestions().size() : 0;
        } catch (Exception e) {
            // Handle lazy initialization exception
            return 0;
        }
    }
}