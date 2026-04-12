package com.exe.skillverse_backend.course_service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;

import java.math.BigDecimal;
import java.time.Instant;

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

    // ========================================================================
    // AI Grading field mapping tests
    // ========================================================================

    @Nested
    @DisplayName("toDetailDto — AI Grading fields")
    class AiGradingFieldTests {

        @Test
        @DisplayName("maps all AI grading fields when populated")
        void toDetailDto_mapsAllAiGradingFields() {
            User learner = User.builder()
                    .id(7L)
                    .email("student@test.com")
                    .build();

            Assignment assignment = Assignment.builder()
                    .id(50L)
                    .title("Assignment")
                    .build();

            Instant gradedAt = Instant.parse("2026-04-07T10:00:00Z");

            AssignmentSubmission submission = AssignmentSubmission.builder()
                    .id(100L)
                    .assignment(assignment)
                    .user(learner)
                    .isAiGraded(true)
                    .aiGradedAt(gradedAt)
                    .aiScore(new BigDecimal("85.50"))
                    .aiFeedback("Good effort")
                    .aiConfidence(0.92)
                    .mentorConfirmed(true)
                    .aiGradeAttemptCount(1)
                    .disputeFlag(false)
                    .disputeAt(null)
                    .disputeReason(null)
                    .build();

            AssignmentSubmissionDetailDTO detail = mapper.toDetailDto(submission);

            assertEquals(true, detail.getIsAiGraded());
            assertEquals(gradedAt, detail.getAiGradedAt());
            assertEquals(new BigDecimal("85.50"), detail.getAiScore());
            assertEquals("Good effort", detail.getAiFeedback());
            assertEquals(0.92, detail.getAiConfidence());
            assertEquals(true, detail.getMentorConfirmed());
            assertEquals(1, detail.getAiGradeAttemptCount());
            assertEquals(false, detail.getDisputeFlag());
            assertNull(detail.getDisputeAt());
            assertNull(detail.getDisputeReason());
        }

        @Test
        @DisplayName("maps all AI grading fields as null when AI grading not used")
        void toDetailDto_aiFieldsNull_whenAiGradingNotUsed() {
            User learner = User.builder()
                    .id(9L)
                    .email("newbie@test.com")
                    .build();

            Assignment assignment = Assignment.builder()
                    .id(60L)
                    .title("New Assignment")
                    .build();

            AssignmentSubmission submission = AssignmentSubmission.builder()
                    .id(200L)
                    .assignment(assignment)
                    .user(learner)
                    .isAiGraded(false)
                    .aiGradedAt(null)
                    .aiScore(null)
                    .aiFeedback(null)
                    .aiConfidence(null)
                    .mentorConfirmed(null)
                    .aiGradeAttemptCount(0)
                    .disputeFlag(false)
                    .disputeAt(null)
                    .disputeReason(null)
                    .build();

            AssignmentSubmissionDetailDTO detail = mapper.toDetailDto(submission);

            assertEquals(false, detail.getIsAiGraded());
            assertNull(detail.getAiGradedAt());
            assertNull(detail.getAiScore());
            assertNull(detail.getAiFeedback());
            assertNull(detail.getAiConfidence());
            assertNull(detail.getMentorConfirmed());
            assertEquals(0, detail.getAiGradeAttemptCount());
            assertEquals(false, detail.getDisputeFlag());
            assertNull(detail.getDisputeAt());
            assertNull(detail.getDisputeReason());
        }
    }
}
