package com.exe.skillverse_backend.course_service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;

class AssignmentSubmissionMapperTest {

    private final AssignmentSubmissionMapper mapper = Mappers.getMapper(AssignmentSubmissionMapper.class);

    @Test
    void toDetailDto_usesEmailPrefixWhenLearnerNameIsMissing() {
        User learner = User.builder()
                .id(7L)
                .email("student.local@test.com")
                .build();

        Assignment assignment = Assignment.builder()
                .id(30L)
                .title("Assignment 1")
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(100L)
                .assignment(assignment)
                .user(learner)
                .build();

        AssignmentSubmissionDetailDTO detail = mapper.toDetailDto(submission);

        assertEquals("student.local", detail.getUserName());
    }

    @Test
    void toDetailDto_usesRoleFallbackWhenLearnerAndEmailAreMissing() {
        User learner = User.builder()
                .id(9L)
                .build();

        Assignment assignment = Assignment.builder()
                .id(31L)
                .title("Assignment 2")
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(101L)
                .assignment(assignment)
                .user(learner)
                .build();

        AssignmentSubmissionDetailDTO detail = mapper.toDetailDto(submission);

        assertEquals("Học viên #9", detail.getUserName());
    }
}
