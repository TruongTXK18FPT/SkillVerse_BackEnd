package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import java.time.Instant;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    config = CustomMapperConfig.class,
    uses = {UserMapper.class, MediaMapper.class},
    imports = {Instant.class}
)
public interface AssignmentSubmissionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "assignmentId", source = "assignment.id")
    @Mapping(target = "assignmentTitle", source = "assignment.title")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", expression = "java(resolveDisplayName(submission.getUser(), \"Học viên\"))")
    @Mapping(target = "fileMediaId", source = "fileMedia.id")
    @Mapping(target = "fileMediaUrl", source = "fileMedia.url")
    @Mapping(target = "submissionText", source = "submissionText")
    @Mapping(target = "linkUrl", source = "linkUrl")
    @Mapping(target = "submittedAt", source = "submittedAt")
    @Mapping(target = "score", source = "score")
    @Mapping(target = "maxScore", source = "assignment.maxScore")
    @Mapping(target = "gradedBy", source = "gradedBy.id")
    @Mapping(target = "gradedByName", expression = "java(resolveDisplayName(submission.getGradedBy(), \"Người chấm\"))")
    @Mapping(target = "gradedAt", source = "gradedAt")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "attemptNumber", source = "attemptNumber")
    @Mapping(target = "isNewest", source = "isNewest")
    @Mapping(target = "isPrevious", source = "isPrevious")
    @Mapping(target = "isLate", source = "isLate")
    AssignmentSubmissionDetailDTO toDetailDto(AssignmentSubmission submission);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "assignment", source = "assignment")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "fileMedia", source = "fileMedia")
    @Mapping(target = "submissionText", source = "createDto.submissionText")
    @Mapping(target = "linkUrl", source = "createDto.linkUrl")
    @Mapping(target = "submittedAt", expression = "java(Instant.now())")
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "gradedBy", ignore = true)
    @Mapping(target = "gradedAt", ignore = true)
    @Mapping(target = "feedback", ignore = true)
    @Mapping(target = "attemptNumber", ignore = true)
    @Mapping(target = "isNewest", constant = "true")
    @Mapping(target = "isPrevious", constant = "false")
    @Mapping(target = "isLate", ignore = true)
    AssignmentSubmission toEntity(AssignmentSubmissionCreateDTO createDto, Assignment assignment, User user, Media fileMedia);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "assignment", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "fileMedia", ignore = true)
    @Mapping(target = "submissionText", ignore = true)
    @Mapping(target = "linkUrl", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "score", source = "grading.score")
    @Mapping(target = "gradedBy", source = "gradedBy")
    @Mapping(target = "gradedAt", expression = "java(Instant.now())")
    @Mapping(target = "feedback", source = "grading.feedback")
    @Mapping(target = "attemptNumber", ignore = true)
    @Mapping(target = "isNewest", ignore = true)
    @Mapping(target = "isPrevious", ignore = true)
    @Mapping(target = "isLate", ignore = true)
    void gradeSubmission(@MappingTarget AssignmentSubmission submission, AssignmentSubmissionDetailDTO grading, User gradedBy);

    default String resolveDisplayName(User user, String fallbackLabel) {
        if (user == null) {
            return fallbackLabel;
        }

        String fullName = user.getFullName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }

        String email = user.getEmail();
        if (email != null && !email.trim().isEmpty()) {
            int atIndex = email.indexOf('@');
            String emailPrefix = atIndex > 0 ? email.substring(0, atIndex) : email;
            if (!emailPrefix.isBlank()) {
                return emailPrefix;
            }
        }

        Long userId = user.getId();
        if (userId != null) {
            return fallbackLabel + " #" + userId;
        }

        return fallbackLabel;
    }
}
