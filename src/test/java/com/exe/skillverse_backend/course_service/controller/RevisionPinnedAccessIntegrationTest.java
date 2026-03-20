package com.exe.skillverse_backend.course_service.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import com.exe.skillverse_backend.course_service.service.LessonService;
import com.exe.skillverse_backend.course_service.service.ModuleService;
import com.exe.skillverse_backend.course_service.service.QuizService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({ModuleController.class, LessonController.class, QuizController.class, AssignmentController.class})
@AutoConfigureMockMvc
@Import(RevisionPinnedAccessIntegrationTest.TestSecurityConfig.class)
class RevisionPinnedAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModuleService moduleService;

    @MockBean
    private LessonService lessonService;

    @MockBean
    private QuizService quizService;

    @MockBean
    private AssignmentService assignmentService;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(org.springframework.security.config.Customizer.withDefaults());
            return http.build();
        }
    }

    @Test
    void learnerSidebarAndDirectItemApis_doNotExposeDraftRevisionContent() throws Exception {
        long learnerId = 77L;
        long courseId = 10L;
        long moduleId = 501L;
        long draftLessonId = 901L;
        long draftQuizId = 902L;
        long draftAssignmentId = 903L;

        QuizSummaryDTO pinnedQuiz = QuizSummaryDTO.builder()
                .id(701L)
                .title("Pinned quiz")
                .description("Visible quiz")
                .passScore(80)
                .maxAttempts(3)
                .gradingMethod(QuizGradingMethod.HIGHEST)
                .orderIndex(2)
                .questionCount(5)
                .build();

        AssignmentSummaryDTO pinnedAssignment = new AssignmentSummaryDTO(
                801L,
                "Pinned assignment",
                "Visible assignment",
                SubmissionType.TEXT,
                new BigDecimal("100"),
                Instant.parse("2026-04-01T00:00:00Z"),
                moduleId,
                3
        );

        ModuleDetailDTO pinnedModule = new ModuleDetailDTO(
                moduleId,
                "Pinned module",
                "Visible from approved snapshot",
                0,
                null,
                null,
                List.of(
                        new LessonBriefDTO(601L, "Pinned reading", LessonType.READING, 0, 120, "A", null, null, null),
                        new LessonBriefDTO(602L, "Pinned video", LessonType.VIDEO, 1, 240, null, null, "https://video.example", null)
                ),
                List.of(pinnedQuiz),
                List.of(pinnedAssignment)
        );

        when(moduleService.listModulesWithContent(courseId, learnerId)).thenReturn(List.of(pinnedModule));
        when(lessonService.listLessonsByModule(moduleId, learnerId)).thenReturn(pinnedModule.getLessons());
        when(quizService.listQuizzesByModule(moduleId, learnerId)).thenReturn(List.of(pinnedQuiz));
        when(assignmentService.listAssignmentsByModule(moduleId, learnerId)).thenReturn(List.of(pinnedAssignment));
        when(lessonService.getLesson(draftLessonId, learnerId)).thenThrow(new NotFoundException("LESSON_NOT_FOUND"));
        when(quizService.getQuizForAttempt(draftQuizId, learnerId)).thenThrow(new NotFoundException("QUIZ_NOT_FOUND"));
        when(assignmentService.getAssignmentById(draftAssignmentId, learnerId))
                .thenThrow(new NotFoundException("ASSIGNMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/courses/{courseId}/modules/full", courseId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].lessons.length()").value(2))
                .andExpect(jsonPath("$[0].lessons[0].id").value(601))
                .andExpect(jsonPath("$[0].lessons[1].id").value(602))
                .andExpect(jsonPath("$[0].quizzes[0].id").value(701))
                .andExpect(jsonPath("$[0].assignments[0].id").value(801));

        mockMvc.perform(get("/api/modules/{moduleId}/lessons", moduleId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("READING"))
                .andExpect(jsonPath("$[1].type").value("VIDEO"));

        mockMvc.perform(get("/api/quizzes/modules/{moduleId}/quizzes", moduleId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(701));

        mockMvc.perform(get("/api/modules/{moduleId}/assignments", moduleId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(801));

        mockMvc.perform(get("/api/lessons/{lessonId}", draftLessonId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/quizzes/{quizId}/attempt-view", draftQuizId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/assignments/{assignmentId}", draftAssignmentId)
                        .with(jwtWithRole(learnerId, "ROLE_USER")))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor jwtWithRole(Long userId, String role) {
        return jwt().jwt(jwt -> jwt.claim("userId", String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
