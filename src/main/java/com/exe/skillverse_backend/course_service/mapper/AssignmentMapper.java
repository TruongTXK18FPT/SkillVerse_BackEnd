package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCriteriaDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class, uses = {UserMapper.class, MediaMapper.class})
public interface AssignmentMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "submissionType", source = "submissionType")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "maxScore", source = "maxScore")
    @Mapping(target = "passingScore", source = "passingScore")
    @Mapping(target = "dueAt", source = "dueAt")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "isRequired", source = "isRequired")
    @Mapping(target = "learningOutcome", source = "learningOutcome")
    @Mapping(target = "gradingCriteria", source = "gradingCriteria")
    @Mapping(target = "criteria", source = "criteria")
    // AI Grading fields
    @Mapping(target = "aiGradingEnabled", source = "aiGradingEnabled")
    @Mapping(target = "aiGradingPrompt", source = "aiGradingPrompt")
    @Mapping(target = "gradingStyle", source = "gradingStyle")
    @Mapping(target = "trustAiEnabled", source = "trustAiEnabled")
    AssignmentDetailDTO toDetailDto(Assignment assignment);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "submissionType", source = "submissionType")
    @Mapping(target = "maxScore", source = "maxScore")
    @Mapping(target = "dueAt", source = "dueAt")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "moduleId", source = "module.id")
    @Mapping(target = "orderIndex", source = "orderIndex")
    AssignmentSummaryDTO toSummaryDto(Assignment assignment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "createDto.title")
    @Mapping(target = "description", source = "createDto.description")
    @Mapping(target = "submissionType", source = "createDto.submissionType")
    @Mapping(target = "orderIndex", source = "createDto.orderIndex")
    @Mapping(target = "maxScore", source = "createDto.maxScore")
    @Mapping(target = "passingScore", source = "createDto.passingScore")
    @Mapping(target = "dueAt", source = "createDto.dueAt")
    @Mapping(target = "isRequired", source = "createDto.isRequired")
    @Mapping(target = "learningOutcome", source = "createDto.learningOutcome")
    @Mapping(target = "gradingCriteria", source = "createDto.gradingCriteria")
    @Mapping(target = "module", source = "module")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "submissions", ignore = true)
    @Mapping(target = "criteria", ignore = true)
    @Mapping(target = "aiGradingEnabled", source = "createDto.aiGradingEnabled", defaultValue = "false")
    @Mapping(target = "aiGradingPrompt", source = "createDto.aiGradingPrompt")
    @Mapping(target = "gradingStyle", source = "createDto.gradingStyle", defaultValue = "STANDARD")
    @Mapping(target = "trustAiEnabled", source = "createDto.trustAiEnabled", defaultValue = "false")
    Assignment toEntity(AssignmentCreateDTO createDto, Module module);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "updateDto.title")
    @Mapping(target = "description", source = "updateDto.description")
    @Mapping(target = "submissionType", source = "updateDto.submissionType")
    @Mapping(target = "orderIndex", source = "updateDto.orderIndex")
    @Mapping(target = "maxScore", source = "updateDto.maxScore")
    @Mapping(target = "passingScore", source = "updateDto.passingScore")
    @Mapping(target = "dueAt", source = "updateDto.dueAt")
    @Mapping(target = "isRequired", source = "updateDto.isRequired")
    @Mapping(target = "learningOutcome", source = "updateDto.learningOutcome")
    @Mapping(target = "gradingCriteria", source = "updateDto.gradingCriteria")
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "submissions", ignore = true)
    @Mapping(target = "criteria", ignore = true)
    // AI Grading fields
    @Mapping(target = "aiGradingEnabled", source = "updateDto.aiGradingEnabled")
    @Mapping(target = "aiGradingPrompt", source = "updateDto.aiGradingPrompt")
    @Mapping(target = "gradingStyle", source = "updateDto.gradingStyle")
    @Mapping(target = "trustAiEnabled", source = "updateDto.trustAiEnabled")
    void updateEntity(@MappingTarget Assignment assignment, AssignmentUpdateDTO updateDto);

    default AssignmentCriteriaDTO toCriteriaDto(AssignmentCriteria criteria) {
        if (criteria == null) return null;
        return AssignmentCriteriaDTO.builder()
                .id(criteria.getId())
                .name(criteria.getName())
                .description(criteria.getDescription())
                .maxPoints(criteria.getMaxPoints())
                .passingPoints(criteria.getPassingPoints())
                .orderIndex(criteria.getOrderIndex())
                .isRequired(criteria.isRequired())
                .build();
    }

    default List<AssignmentCriteriaDTO> mapCriteria(List<AssignmentCriteria> criteria) {
        if (criteria == null) return List.of();
        return criteria.stream().map(this::toCriteriaDto).collect(Collectors.toList());
    }
}
