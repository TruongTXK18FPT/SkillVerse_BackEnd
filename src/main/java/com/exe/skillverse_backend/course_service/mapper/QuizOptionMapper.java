package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.course_service.dto.quizdto.QuizOptionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizOptionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizOptionUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface QuizOptionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "optionText", source = "optionText")
    @Mapping(target = "correct", source = "isCorrect")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "orderIndex", source = "orderIndex")
    QuizOptionDetailDTO toDetailDto(QuizOption option);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "optionText", source = "createDto.optionText")
    @Mapping(target = "isCorrect", source = "createDto.correct")
    @Mapping(target = "feedback", ignore = true) // Not provided in frontend
    @Mapping(target = "orderIndex", source = "createDto.orderIndex")
    @Mapping(target = "question", source = "question")
    QuizOption toEntity(QuizOptionCreateDTO createDto, QuizQuestion question);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "optionText", source = "updateDto.optionText")
    @Mapping(target = "isCorrect", source = "updateDto.correct")
    @Mapping(target = "feedback", ignore = true) // Not provided in frontend
    @Mapping(target = "orderIndex", source = "updateDto.orderIndex")
    @Mapping(target = "question", ignore = true)
    void updateEntity(@MappingTarget QuizOption option, QuizOptionUpdateDTO updateDto);
}
