package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.entity.CodingExercise;
import com.exe.skillverse_backend.course_service.entity.CodingSubmission;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import java.time.Instant;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    config = CustomMapperConfig.class,
    uses = {UserMapper.class},
    imports = {Instant.class}
)
public interface CodingSubmissionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", expression = "java((submission.getUser().getFirstName() + \" \" + submission.getUser().getLastName()).trim())")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "submittedAt", source = "submittedAt")
    CodingSubmissionDetailDTO toDetailDto(CodingSubmission submission);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "exercise", source = "exercise")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "submittedCode", source = "createDto.code")
    @Mapping(target = "status", constant = "QUEUED")
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "feedback", ignore = true)
    @Mapping(target = "submittedAt", expression = "java(Instant.now())")
    CodingSubmission toEntity(CodingSubmissionCreateDTO createDto, CodingExercise exercise, User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "exercise", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "submittedCode", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "feedback", source = "feedback")
    void updateSubmissionResult(@MappingTarget CodingSubmission submission, CodingSubmissionDetailDTO result);
}
