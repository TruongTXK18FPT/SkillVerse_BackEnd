package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class, uses = {UserMapper.class, MediaMapper.class})
public interface AssignmentSubmissionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "fileMedia", source = "fileMedia")
    @Mapping(target = "submissionText", source = "submissionText")
    @Mapping(target = "linkUrl", source = "linkUrl")
    @Mapping(target = "submittedAt", source = "submittedAt")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "gradedBy", source = "gradedBy")
    @Mapping(target = "feedback", source = "feedback")
    AssignmentSubmissionDetailDTO toDetailDto(AssignmentSubmission submission);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "assignment", source = "assignment")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "fileMedia", source = "fileMedia")
    @Mapping(target = "submissionText", source = "submissionText")
    @Mapping(target = "linkUrl", source = "linkUrl")
    @Mapping(target = "submittedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "gradedBy", ignore = true)
    @Mapping(target = "feedback", ignore = true)
    AssignmentSubmission toEntity(AssignmentSubmissionCreateDTO createDto, Assignment assignment, User user, Media fileMedia);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "assignment", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "fileMedia", ignore = true)
    @Mapping(target = "submissionText", ignore = true)
    @Mapping(target = "linkUrl", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "score", source = "score")
    @Mapping(target = "gradedBy", source = "gradedBy")
    @Mapping(target = "feedback", source = "feedback")
    void gradeSubmission(@MappingTarget AssignmentSubmission submission, AssignmentSubmissionDetailDTO grading, User gradedBy);
}