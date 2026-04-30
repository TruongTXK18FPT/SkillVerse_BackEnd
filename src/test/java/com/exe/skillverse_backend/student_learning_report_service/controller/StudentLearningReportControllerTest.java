package com.exe.skillverse_backend.student_learning_report_service.controller;

import com.exe.skillverse_backend.student_learning_report_service.dto.response.LearningReportTimelineResponse;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.service.StudentLearningReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentLearningReportController.class)
@AutoConfigureMockMvc
@Import(StudentLearningReportControllerTest.TestSecurityConfig.class)
class StudentLearningReportControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }));
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StudentLearningReportService reportService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private StudentLearningReportResponse reportResponse;
    private LearningReportTimelineResponse timelineResponse;

    @BeforeEach
    void setUp() {
        reportResponse = StudentLearningReportResponse.builder()
                .id(10L)
                .reportId(10L)
                .reportName("Báo cáo 22/04/2026")
                .generatedAt(LocalDateTime.of(2026, 4, 22, 10, 30))
                .studentId(1L)
                .studentName("Student One")
                .reportType("COMPREHENSIVE")
                .range("30d")
                .snapshot(true)
                .overview(StudentLearningReportResponse.Overview.builder()
                        .overallProgress(42)
                        .learningTrend("stable")
                        .recommendations(List.of(StudentLearningReportResponse.Recommendation.builder()
                                .id("study-keep")
                                .tier("STRENGTH")
                                .category("STUDY")
                                .title("Giữ nhịp học đều.")
                                .build()))
                        .build())
                .studyStats(StudentLearningReportResponse.StudyStats.builder()
                        .studyMinutesToday(30)
                        .studyMinutesWeek(180)
                        .studyMinutesMonth(420)
                        .totalStudyHours(24)
                        .currentStreak(4)
                        .build())
                .roadmapStats(StudentLearningReportResponse.RoadmapStats.builder()
                        .totalRoadmaps(2)
                        .completedRoadmaps(0)
                        .inProgressRoadmaps(2)
                        .totalMissions(8)
                        .completedMissions(3)
                        .pendingMissions(5)
                        .roadmapProgress(38)
                        .build())
                .taskStats(StudentLearningReportResponse.TaskStats.builder()
                        .totalTasks(6)
                        .completedTasks(2)
                        .pendingTasks(4)
                        .overdueTasks(1)
                        .taskProgress(33)
                        .build())
                .courseStats(StudentLearningReportResponse.CourseStats.builder()
                        .activeCourses(2)
                        .completedCourses(1)
                        .averageActiveCourseProgress(55)
                        .build())
                .timeline(List.of(StudentLearningReportResponse.TimelinePoint.builder()
                        .bucketLabel("22/04")
                        .bucketStart(LocalDate.of(2026, 4, 22))
                        .studyMinutes(30)
                        .missionsCompleted(1)
                        .build()))
                .overallProgress(42)
                .learningTrend("stable")
                .recommendedFocus("Giữ nhịp học đều.")
                .build();

        timelineResponse = LearningReportTimelineResponse.builder()
                .range("7d")
                .snapshotId(10L)
                .generatedAt(LocalDateTime.of(2026, 4, 22, 10, 30))
                .timeline(reportResponse.getTimeline())
                .build();
    }

    @Test
    @DisplayName("GET /summary should return live summary")
    void getSummary_ShouldReturnLiveSummary() throws Exception {
        when(reportService.getSummary(1L, "30d")).thenReturn(reportResponse);

        mockMvc.perform(get("/api/student/learning-report/summary")
                        .param("range", "30d")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallProgress").value(42))
                .andExpect(jsonPath("$.overview.recommendations[0].title").value("Giữ nhịp học đều."))
                .andExpect(jsonPath("$.overview.recommendations[0].tier").value("STRENGTH"));
    }

    @Test
    @DisplayName("GET /timeline should return chart data")
    void getTimeline_ShouldReturnTimeline() throws Exception {
        when(reportService.getTimeline(1L, "7d", 10L)).thenReturn(timelineResponse);

        mockMvc.perform(get("/api/student/learning-report/timeline")
                        .param("range", "7d")
                        .param("snapshotId", "10")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("7d"))
                .andExpect(jsonPath("$.timeline[0].studyMinutes").value(30));
    }

    @Test
    @DisplayName("POST /snapshots should save snapshot")
    void createSnapshot_ShouldReturnSavedSnapshot() throws Exception {
        when(reportService.createSnapshot(1L, "30d")).thenReturn(reportResponse);

        mockMvc.perform(post("/api/student/learning-report/snapshots")
                        .with(csrf())
                        .param("range", "30d")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(10));
    }

    @Test
    @DisplayName("GET /history should return saved snapshots")
    void getHistory_ShouldReturnSnapshots() throws Exception {
        when(reportService.getReportHistory(1L, 0, 10)).thenReturn(List.of(reportResponse));

        mockMvc.perform(get("/api/student/learning-report/history")
                        .param("page", "0")
                        .param("size", "10")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value(10));
    }

    @Test
    @DisplayName("GET /latest should return 204 when no snapshot exists")
    void getLatest_ShouldReturnNoContentWhenMissing() throws Exception {
        when(reportService.getLatestReport(1L)).thenReturn(null);

        mockMvc.perform(get("/api/student/learning-report/latest")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("GET /{id} should return snapshot detail")
    void getReportById_ShouldReturnSnapshotDetail() throws Exception {
        when(reportService.getReportById(1L, 10L)).thenReturn(reportResponse);

        mockMvc.perform(get("/api/student/learning-report/10")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportName").value("Báo cáo 22/04/2026"));
    }

    @Test
    @DisplayName("POST /generate should call legacy alias")
    void generate_ShouldReturnAliasResponse() throws Exception {
        when(reportService.generateLearningReport(eq(1L), any())).thenReturn(reportResponse);

        mockMvc.perform(post("/api/student/learning-report/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("range", "30d")))
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.claim("userId", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(10));
    }
}
