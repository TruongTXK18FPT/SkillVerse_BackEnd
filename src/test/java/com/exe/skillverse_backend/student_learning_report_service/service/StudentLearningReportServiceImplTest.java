package com.exe.skillverse_backend.student_learning_report_service.service;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.LearningReportTimelineResponse;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.entity.StudentLearningReport;
import com.exe.skillverse_backend.student_learning_report_service.repository.StudentLearningReportRepository;
import com.exe.skillverse_backend.student_learning_report_service.service.impl.StudentLearningReportServiceImpl;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentLearningReportServiceImplTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
    private ShortTermJobApplicationRepository shortTermJobApplicationRepository;

    @Mock
    private JobReviewRepository jobReviewRepository;

    private StudentLearningReportServiceImpl service;
    private ObjectMapper objectMapper;
    private User student;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        service = new StudentLearningReportServiceImpl(
                reportRepository,
                userRepository,
                roadmapSessionRepository,
                studySessionRepository,
                taskRepository,
                courseEnrollmentRepository,
                shortTermJobApplicationRepository,
                jobReviewRepository,
                objectMapper,
                new com.exe.skillverse_backend.student_learning_report_service.service.recommendation.RecommendationEngine());

        student = User.builder()
                .id(1L)
                .firstName("Student")
                .lastName("One")
                .email("student.one@example.com")
                .build();
    }

    @Test
    @DisplayName("getSummary should compute deterministic metrics, exclude archived tasks, and compare against latest snapshot")
    void getSummary_ShouldComputeDeterministicMetricsAndRecommendations() throws Exception {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        RoadmapSession roadmap = RoadmapSession.builder()
                .id(10L)
                .user(student)
                .title("Backend Java")
                .validatedGoal("Become backend developer")
                .totalNodes(4)
                .roadmapJson("""
                        {"roadmap":[
                          {"id":"node-1","title":"Java Basics"},
                          {"id":"node-2","title":"OOP"},
                          {"id":"node-3","title":"Spring Boot"},
                          {"id":"node-4","title":"Deployment"}
                        ]}
                        """)
                .progressList(List.of(UserRoadmapProgress.builder()
                        .questId("node-1")
                        .status(UserRoadmapProgress.ProgressStatus.COMPLETED)
                        .completedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                        .build()))
                .build();

        List<Task> tasks = List.of(
                Task.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .status("DONE")
                        .userProgress(100)
                        .archived(false)
                        .build(),
                Task.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .status("TODO")
                        .userProgress(20)
                        .deadline(now.minusDays(1))
                        .archived(false)
                        .build(),
                Task.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .status("DONE")
                        .userProgress(100)
                        .archived(true)
                        .build());

        List<StudySession> studySessions = List.of(StudySession.builder()
                .id(UUID.randomUUID())
                .user(student)
                .title("Yesterday session")
                .startTime(now.minusDays(1).withHour(20).withMinute(0))
                .endTime(now.minusDays(1).withHour(21).withMinute(0))
                .build());

        List<CourseEnrollment> enrollments = List.of(
                CourseEnrollment.builder()
                        .user(student)
                        .course(Course.builder().id(101L).title("Spring Boot Mastery").build())
                        .status(EnrollmentStatus.ENROLLED)
                        .progressPercent(30)
                        .enrollDate(Instant.now().minus(20, ChronoUnit.DAYS))
                        .build(),
                CourseEnrollment.builder()
                        .user(student)
                        .course(Course.builder().id(102L).title("Git Fundamentals").build())
                        .status(EnrollmentStatus.COMPLETED)
                        .progressPercent(100)
                        .completedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                        .enrollDate(Instant.now().minus(35, ChronoUnit.DAYS))
                        .build());

        StudentLearningReport previousSnapshot = StudentLearningReport.builder()
                .id(2L)
                .student(student)
                .generatedAt(now.minusDays(7))
                .averageProgressSnapshot(20)
                .summarySnapshot(objectMapper.writeValueAsString(StudentLearningReportResponse.builder()
                        .overview(StudentLearningReportResponse.Overview.builder()
                                .overallProgress(20)
                                .learningTrend("stable")
                                .recommendations(List.of(StudentLearningReportResponse.Recommendation.builder()
                                        .id("legacy-prev")
                                        .tier("IMPROVE")
                                        .category("GROWTH")
                                        .title("Old recommendation")
                                        .build()))
                                .build())
                        .build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(roadmapSessionRepository.findByUserIdAndStatusNotDeleted(1L)).thenReturn(List.of(roadmap));
        when(studySessionRepository.findByUserId(1L)).thenReturn(studySessions);
        when(taskRepository.findByUserId(1L)).thenReturn(tasks);
        when(courseEnrollmentRepository.findByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(enrollments));
        when(reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(1L))
                .thenReturn(Optional.of(previousSnapshot));

        StudentLearningReportResponse summary = service.getSummary(1L, "30d");

        assertEquals(1, summary.getRoadmapStats().getTotalRoadmaps());
        assertEquals(4, summary.getRoadmapStats().getTotalMissions());
        assertEquals(1, summary.getRoadmapStats().getCompletedMissions());
        assertEquals(2, summary.getTaskStats().getTotalTasks());
        assertEquals(1, summary.getTaskStats().getCompletedTasks());
        assertEquals(1, summary.getTaskStats().getOverdueTasks());
        assertEquals(0, summary.getStudyStats().getCurrentStreak());
        assertEquals(1, summary.getCourseStats().getActiveCourses());
        assertEquals(1, summary.getCourseStats().getCompletedCourses());
        assertEquals(30, summary.getCourseStats().getAverageActiveCourseProgress());
        assertEquals(35, summary.getOverallProgress());
        assertEquals("improving", summary.getLearningTrend());
        java.util.List<StudentLearningReportResponse.Recommendation> recs = summary.getOverview().getRecommendations();
        assertTrue(recs.size() >= 3, "engine should emit multiple recommendations");
        assertTrue(recs.stream().anyMatch(r -> "STUDY".equals(r.getCategory())),
                "expected a STUDY recommendation");
        assertTrue(recs.stream().anyMatch(r -> "ROADMAP".equals(r.getCategory())),
                "expected a ROADMAP recommendation");
        assertTrue(recs.stream().anyMatch(r -> "TASK".equals(r.getCategory())),
                "expected a TASK recommendation");
        assertTrue(recs.stream().allMatch(r -> r.getTitle() != null && !r.getTitle().isBlank()),
                "every recommendation should have a title");
        verify(roadmapSessionRepository).findByUserIdAndStatusNotDeleted(1L);
        verify(roadmapSessionRepository, never()).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("getSummary should return streak zero when there is no study today")
    void getSummary_ShouldReturnZeroStreakWithoutStudyToday() {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(roadmapSessionRepository.findByUserIdAndStatusNotDeleted(1L)).thenReturn(List.of());
        when(studySessionRepository.findByUserId(1L)).thenReturn(List.of(
                StudySession.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .startTime(now.minusDays(1).withHour(8))
                        .endTime(now.minusDays(1).withHour(9))
                        .build(),
                StudySession.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .startTime(now.minusDays(2).withHour(8))
                        .endTime(now.minusDays(2).withHour(9))
                        .build()));
        when(taskRepository.findByUserId(1L)).thenReturn(List.of());
        when(courseEnrollmentRepository.findByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(1L))
                .thenReturn(Optional.empty());

        StudentLearningReportResponse summary = service.getSummary(1L, "7d");

        assertEquals(0, summary.getStudyStats().getCurrentStreak());
        assertEquals(7, summary.getTimeline().size());
    }

    @Test
    @DisplayName("getTimeline should build 7d, 30d, and 90d buckets with study and mission counts")
    void getTimeline_ShouldBuildExpectedBuckets() {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        RoadmapSession roadmap = RoadmapSession.builder()
                .id(11L)
                .user(student)
                .title("Roadmap")
                .totalNodes(2)
                .roadmapJson("""
                        {"roadmap":[
                          {"id":"n1","title":"Start"},
                          {"id":"n2","title":"Finish"}
                        ]}
                        """)
                .progressList(List.of(
                        UserRoadmapProgress.builder()
                                .questId("n1")
                                .status(UserRoadmapProgress.ProgressStatus.COMPLETED)
                                .completedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                                .build(),
                        UserRoadmapProgress.builder()
                                .questId("n2")
                                .status(UserRoadmapProgress.ProgressStatus.COMPLETED)
                                .completedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                                .build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(roadmapSessionRepository.findByUserIdAndStatusNotDeleted(1L)).thenReturn(List.of(roadmap));
        when(studySessionRepository.findByUserId(1L)).thenReturn(List.of(
                StudySession.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .startTime(now.minusDays(1).withHour(19))
                        .endTime(now.minusDays(1).withHour(20))
                        .build(),
                StudySession.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .startTime(now.minusDays(6).withHour(19))
                        .endTime(now.minusDays(6).withHour(19).plusMinutes(45))
                        .build(),
                StudySession.builder()
                        .id(UUID.randomUUID())
                        .user(student)
                        .startTime(now.minusDays(40).withHour(19))
                        .endTime(now.minusDays(40).withHour(19).plusMinutes(50))
                        .build()));
        when(taskRepository.findByUserId(1L)).thenReturn(List.of());
        when(courseEnrollmentRepository.findByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(1L))
                .thenReturn(Optional.empty());

        LearningReportTimelineResponse sevenDays = service.getTimeline(1L, "7d", null);
        LearningReportTimelineResponse thirtyDays = service.getTimeline(1L, "30d", null);
        LearningReportTimelineResponse ninetyDays = service.getTimeline(1L, "90d", null);

        assertEquals(7, sevenDays.getTimeline().size());
        assertEquals(30, thirtyDays.getTimeline().size());
        assertEquals(13, ninetyDays.getTimeline().size());
        assertEquals(105, sevenDays.getTimeline().stream().mapToInt(item -> item.getStudyMinutes()).sum());
        assertEquals(105, thirtyDays.getTimeline().stream().mapToInt(item -> item.getStudyMinutes()).sum());
        assertEquals(155, ninetyDays.getTimeline().stream().mapToInt(item -> item.getStudyMinutes()).sum());
        assertEquals(1, sevenDays.getTimeline().stream().mapToInt(item -> item.getMissionsCompleted()).sum());
        assertEquals(2, thirtyDays.getTimeline().stream().mapToInt(item -> item.getMissionsCompleted()).sum());
    }

    @Test
    @DisplayName("createSnapshot and history should read from summary_snapshot instead of live metrics")
    void createSnapshotAndHistory_ShouldUseStoredSummarySnapshot() throws Exception {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        RoadmapSession roadmap = RoadmapSession.builder()
                .id(12L)
                .user(student)
                .title("Algorithms")
                .totalNodes(2)
                .roadmapJson("""
                        {"roadmap":[
                          {"id":"a1","title":"Arrays"},
                          {"id":"a2","title":"Graphs"}
                        ]}
                        """)
                .progressList(List.of(UserRoadmapProgress.builder()
                        .questId("a1")
                        .status(UserRoadmapProgress.ProgressStatus.COMPLETED)
                        .completedAt(Instant.now().minus(3, ChronoUnit.DAYS))
                        .build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(roadmapSessionRepository.findByUserIdAndStatusNotDeleted(1L)).thenReturn(List.of(roadmap));
        when(studySessionRepository.findByUserId(1L)).thenReturn(List.of(StudySession.builder()
                .id(UUID.randomUUID())
                .user(student)
                .startTime(now.withHour(8))
                .endTime(now.withHour(9))
                .build()));
        when(taskRepository.findByUserId(1L)).thenReturn(List.of(Task.builder()
                .id(UUID.randomUUID())
                .user(student)
                .status("DONE")
                .userProgress(100)
                .archived(false)
                .build()));
        when(courseEnrollmentRepository.findByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(1L))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any(StudentLearningReport.class))).thenAnswer(invocation -> {
            StudentLearningReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(99L);
            }
            return report;
        });

        StudentLearningReportResponse snapshot = service.createSnapshot(1L, "30d");

        StudentLearningReport storedReport = StudentLearningReport.builder()
                .id(99L)
                .student(student)
                .studentName("Student One")
                .generatedAt(now)
                .summarySnapshot(objectMapper.writeValueAsString(snapshot))
                .build();

        when(reportRepository.findByStudentIdOrderByGeneratedAtDescIdDesc(1L))
                .thenReturn(List.of(storedReport));

        List<StudentLearningReportResponse> history = service.getReportHistory(1L);

        assertEquals(1, history.size());
        assertEquals(snapshot.getOverallProgress(), history.get(0).getOverallProgress());
        assertEquals(snapshot.getTaskStats().getCompletedTasks(), history.get(0).getTaskStats().getCompletedTasks());
        assertFalse(history.get(0).getTimeline().isEmpty());
    }
}
