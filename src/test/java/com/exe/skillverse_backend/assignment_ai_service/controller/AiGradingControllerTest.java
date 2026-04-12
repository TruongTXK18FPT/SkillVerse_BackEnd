package com.exe.skillverse_backend.assignment_ai_service.controller;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingResultDTO;
import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentAiGradingService;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiGradingController.class)
@DisplayName("AiGradingController")
class AiGradingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssignmentAiGradingService aiGradingService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private User student;
    private User mentor;
    private Course course;
    private Module module;
    private Assignment assignment;
    private AssignmentSubmission submission;

    @BeforeEach
    void setUp() {
        student = User.builder().id(7L).primaryRole(PrimaryRole.USER).firstName("Student").lastName("One").build();
        mentor = User.builder().id(8L).primaryRole(PrimaryRole.MENTOR).firstName("Mentor").lastName("One").build();
        course = Course.builder().id(30L).author(mentor).build();
        module = Module.builder().id(10L).course(course).build();
        assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .title("AI Assignment")
                .build();
        submission = AssignmentSubmission.builder()
                .id(100L)
                .assignment(assignment)
                .user(student)
                .isAiGraded(true)
                .aiScore(new BigDecimal("85"))
                .aiFeedback("Good work")
                .aiConfidence(0.90)
                .mentorConfirmed(null)
                .disputeFlag(false)
                .build();
    }

    private Jwt mockJwt(Long userId, PrimaryRole role) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", role.name())
                .build();
    }

    // ========================================================================
    // requestMentorReview (dispute) tests
    // ========================================================================

    @Test
    @DisplayName("requestMentorReview by submission owner delegates to service")
    void requestMentorReview_byOwner_delegatesToService() throws Exception {
        mockMvc.perform(put("/api/ai-grading/dispute/{submissionId}", 100L)
                        .with(jwt().jwt(mockJwt(7L, PrimaryRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("Too harsh for an AI grade"))
                .andExpect(status().isOk());

        verify(aiGradingService).requestMentorReview(
                eq(100L), eq(7L), eq("Too harsh for an AI grade"));
    }

    @Test
    @DisplayName("requestMentorReview by non-owner still delegates — auth handled by service")
    void requestMentorReview_byNonOwner_delegatesToService() throws Exception {
        mockMvc.perform(put("/api/ai-grading/dispute/{submissionId}", 100L)
                        .with(jwt().jwt(mockJwt(99L, PrimaryRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Controller delegates to service; service throws AccessDeniedException
        verify(aiGradingService).requestMentorReview(eq(100L), eq(99L), any());
    }

    @Test
    @DisplayName("requestMentorReview for non-existent submission returns 404")
    void requestMentorReview_submissionNotFound_throws404() throws Exception {
        doThrow(new com.exe.skillverse_backend.shared.exception.NotFoundException("SUBMISSION_NOT_FOUND"))
                .when(aiGradingService).requestMentorReview(anyLong(), anyLong(), any());

        mockMvc.perform(put("/api/ai-grading/dispute/{submissionId}", 999L)
                        .with(jwt().jwt(mockJwt(7L, PrimaryRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ========================================================================
    // toggleTrustAi tests
    // ========================================================================

    @Test
    @DisplayName("toggleTrustAi enables trust and delegates to service")
    void toggleTrustAi_enablesTrust_delegatesToService() throws Exception {
        mockMvc.perform(put("/api/ai-grading/assignment/{assignmentId}/trust-ai", 50L)
                        .with(jwt().jwt(mockJwt(8L, PrimaryRole.MENTOR)))
                        .param("enabled", "true"))
                .andExpect(status().isOk());

        verify(aiGradingService).toggleTrustAi(eq(50L), eq(true));
    }

    @Test
    @DisplayName("toggleTrustAi disables trust and delegates to service")
    void toggleTrustAi_disablesTrust_delegatesToService() throws Exception {
        mockMvc.perform(put("/api/ai-grading/assignment/{assignmentId}/trust-ai", 50L)
                        .with(jwt().jwt(mockJwt(8L, PrimaryRole.MENTOR)))
                        .param("enabled", "false"))
                .andExpect(status().isOk());

        verify(aiGradingService).toggleTrustAi(eq(50L), eq(false));
    }

    @Test
    @DisplayName("toggleTrustAi returns 404 when assignment not found")
    void toggleTrustAi_assignmentNotFound_throws404() throws Exception {
        doThrow(new com.exe.skillverse_backend.shared.exception.NotFoundException("ASSIGNMENT_NOT_FOUND"))
                .when(aiGradingService).toggleTrustAi(anyLong(), anyBoolean());

        mockMvc.perform(put("/api/ai-grading/assignment/{assignmentId}/trust-ai", 999L)
                        .with(jwt().jwt(mockJwt(8L, PrimaryRole.MENTOR)))
                        .param("enabled", "true"))
                .andExpect(status().isNotFound());
    }

    // ========================================================================
    // getAiGradeResult tests
    // ========================================================================

    @Test
    @DisplayName("getAiGradeResult returns AI grading result")
    void getAiGradeResult_returnsResult() throws Exception {
        AiGradingResultDTO result = new AiGradingResultDTO();
        result.setTotalScore(new BigDecimal("85"));
        result.setOverallFeedback("Good work");
        result.setOverallConfidence(0.90);
        result.setCriteriaScores(List.of());

        when(aiGradingService.getAiGradeResult(100L)).thenReturn(result);

        mockMvc.perform(get("/api/ai-grading/result/{submissionId}", 100L)
                        .with(jwt().jwt(mockJwt(8L, PrimaryRole.MENTOR))))
                .andExpect(status().isOk());
    }

    // ========================================================================
    // generateAiGrade tests
    // ========================================================================

    @Test
    @DisplayName("generateAiGrade triggers AI grading and returns result")
    void generateAiGrade_triggersAiGrading() throws Exception {
        AiGradingResultDTO result = new AiGradingResultDTO();
        result.setTotalScore(new BigDecimal("78"));
        result.setOverallFeedback("Decent work");
        result.setOverallConfidence(0.85);
        result.setCriteriaScores(List.of());

        when(aiGradingService.generateAiGrade(eq(100L), eq(8L))).thenReturn(result);

        mockMvc.perform(post("/api/ai-grading/generate/{submissionId}", 100L)
                        .with(jwt().jwt(mockJwt(8L, PrimaryRole.MENTOR))))
                .andExpect(status().isOk());
    }
}
