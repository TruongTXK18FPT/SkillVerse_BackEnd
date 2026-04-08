package com.exe.skillverse_backend.student_learning_report_service.service;

import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.service.AiChatbotService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.entity.StudentLearningReport;
import com.exe.skillverse_backend.student_learning_report_service.repository.StudentLearningReportRepository;
import com.exe.skillverse_backend.student_learning_report_service.service.impl.StudentLearningReportServiceImpl;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentLearningReportServiceImplTest {

    @Mock
    private StudentLearningReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoadmapSessionRepository roadmapSessionRepository;

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private AiChatbotService aiChatbotService;

    @Mock
    private ChatModel learningReportChatModel;

    private StudentLearningReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentLearningReportServiceImpl(
                reportRepository,
                userRepository,
                roadmapSessionRepository,
                studySessionRepository,
                taskRepository,
                courseEnrollmentRepository,
                journeyRepository,
                aiChatbotService,
                learningReportChatModel);
    }

    @Test
    @DisplayName("generateLearningReport should enforce the comprehensive report cooldown")
    void generateLearningReport_ShouldEnforceTheComprehensiveReportCooldown() {
        User student = User.builder().id(1L).email("student@skillverse.vn").firstName("Student").lastName("One").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        // Report was generated 1 hour ago — still within 6-hour cooldown
        when(reportRepository.findLatestComprehensiveGeneratedAt(1L))
                .thenReturn(LocalDateTime.now().minusHours(1));

        GenerateStudentReportRequest request = GenerateStudentReportRequest.builder()
                .reportType(StudentLearningReport.ReportType.COMPREHENSIVE)
                .build();

        ApiException exception = assertThrows(ApiException.class,
                () -> service.generateLearningReport(1L, request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("generateLearningReport should return a fallback report when AI is disabled")
    void generateLearningReport_ShouldReturnFallbackReportWhenAiIsDisabled() {
        ReflectionTestUtils.setField(service, "aiEnabled", false);

        User student = User.builder().id(1L).email("student@skillverse.vn").firstName("Student").lastName("One").build();
        RoadmapSession roadmap = RoadmapSession.builder()
                .id(10L)
                .user(student)
                .title("Java roadmap")
                .originalGoal("Learn Java")
                .validatedGoal("Learn Java well")
                .totalNodes(2)
                .createdAt(Instant.now())
                .progressList(List.of(UserRoadmapProgress.builder()
                        .questId("node-1")
                        .status(UserRoadmapProgress.ProgressStatus.COMPLETED)
                        .build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(reportRepository.findLatestComprehensiveGeneratedAt(1L)).thenReturn(null);
        when(roadmapSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(roadmap));
        when(studySessionRepository.findByUserId(1L)).thenReturn(List.of(StudySession.builder()
                .id(UUID.randomUUID())
                .title("Session")
                .user(student)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .build()));
        when(taskRepository.findByUserId(1L)).thenReturn(List.of(
                Task.builder().id(UUID.randomUUID()).user(student).status("DONE").userProgress(100).build(),
                Task.builder().id(UUID.randomUUID()).user(student).status("TODO").userProgress(0).build()));
        when(courseEnrollmentRepository.findActiveEnrollmentsByUserId(1L)).thenReturn(List.of(CourseEnrollment.builder()
                .user(student)
                .course(Course.builder().id(5L).title("Java").build())
                .progressPercent(100)
                .build()));
        when(aiChatbotService.getUserSessions(1L)).thenReturn(List.of(ChatSessionSummary.builder()
                .sessionId(50L)
                .title("Career chat")
                .build()));

        StudentLearningReportResponse response = service.generateLearningReport(
                1L,
                GenerateStudentReportRequest.builder().build());

        assertEquals("COMPREHENSIVE", response.getReportType());
        assertEquals("Student One", response.getStudentName());
        assertEquals(1, response.getMetrics().getTotalRoadmaps());
        assertEquals(50, response.getMetrics().getAverageProgress());
        assertEquals(2, response.getMetrics().getTotalTasks());
        assertEquals(1, response.getMetrics().getCompletedCourses());
        assertNotNull(response.getSections());
    }

    @Test
    @DisplayName("getReportById should block access to another student's report")
    void getReportById_ShouldBlockAccessToAnotherStudentsReport() {
        User owner = User.builder().id(1L).email("owner@skillverse.vn").firstName("Owner").lastName("One").build();
        StudentLearningReport report = StudentLearningReport.builder()
                .id(5L)
                .student(owner)
                .studentName("Owner One")
                .reportContent("content")
                .reportType(StudentLearningReport.ReportType.COMPREHENSIVE)
                .build();

        when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

        ApiException exception = assertThrows(ApiException.class, () -> service.getReportById(2L, 5L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }
}
