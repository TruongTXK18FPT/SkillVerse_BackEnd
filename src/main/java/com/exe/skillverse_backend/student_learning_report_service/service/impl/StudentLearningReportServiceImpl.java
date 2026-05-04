package com.exe.skillverse_backend.student_learning_report_service.service.impl;

import java.math.BigDecimal;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.JobDeliverable;
import com.exe.skillverse_backend.business_service.entity.JobReview;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.LearningReportTimelineResponse;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.entity.StudentLearningReport;
import com.exe.skillverse_backend.student_learning_report_service.repository.StudentLearningReportRepository;
import com.exe.skillverse_backend.student_learning_report_service.service.StudentLearningReportService;
import com.exe.skillverse_backend.student_learning_report_service.service.recommendation.RecommendationEngine;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentLearningReportServiceImpl implements StudentLearningReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DEFAULT_RANGE = "30d";
    private static final Set<String> SUPPORTED_RANGES = Set.of("7d", "30d", "90d");
    private static final DateTimeFormatter REPORT_NAME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private final StudentLearningReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final StudySessionRepository studySessionRepository;
    private final TaskRepository taskRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ShortTermJobApplicationRepository jobApplicationRepository;
    private final JobReviewRepository jobReviewRepository;
    private final ObjectMapper objectMapper;
    private final RecommendationEngine recommendationEngine;

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse getSummary(Long studentId, String range) {
        User student = getStudent(studentId);
        String normalizedRange = normalizeRange(range);
        return buildLiveResponse(student, normalizedRange, false);
    }

    @Override
    @Transactional(readOnly = true)
    public LearningReportTimelineResponse getTimeline(Long studentId, String range, Long snapshotId) {
        String normalizedRange = normalizeRange(range);

        if (snapshotId != null) {
            StudentLearningReport report = getOwnedReport(studentId, snapshotId);
            StudentLearningReportResponse snapshot = toSnapshotResponse(report, normalizedRange);
            return LearningReportTimelineResponse.builder()
                    .range(normalizedRange)
                    .snapshotId(report.getId())
                    .generatedAt(report.getGeneratedAt())
                    .timeline(defaultList(snapshot.getTimeline()))
                    .build();
        }

        User student = getStudent(studentId);
        StudentLearningReportResponse live = buildLiveResponse(student, normalizedRange, false);
        return LearningReportTimelineResponse.builder()
                .range(normalizedRange)
                .generatedAt(live.getGeneratedAt())
                .timeline(defaultList(live.getTimeline()))
                .build();
    }

    @Override
    @Transactional
    public StudentLearningReportResponse createSnapshot(Long studentId, String range) {
        User student = getStudent(studentId);
        String normalizedRange = normalizeRange(range);

        StudentLearningReportResponse response = buildLiveResponse(student, normalizedRange, true);
        response.setReportType(StudentLearningReport.ReportType.COMPREHENSIVE.name());

        StudentLearningReport entity = StudentLearningReport.builder()
                .student(student)
                .studentName(response.getStudentName())
                .generatedAt(response.getGeneratedAt())
                .isAiGenerated(false)
                .reportType(StudentLearningReport.ReportType.COMPREHENSIVE)
                .averageProgressSnapshot(getOverallProgress(response))
                .learningTrend(getLearningTrend(response))
                .recommendedFocus(getRecommendedFocus(response))
                .totalStudyHoursSnapshot(response.getStudyStats() != null ? response.getStudyStats().getTotalStudyHours() : 0)
                .streakDaysSnapshot(response.getStudyStats() != null ? response.getStudyStats().getCurrentStreak() : 0)
                .tasksCompletedSnapshot(response.getTaskStats() != null ? response.getTaskStats().getCompletedTasks() : 0)
                .build();

        entity = reportRepository.save(entity);
        response.setId(entity.getId());
        response.setReportId(entity.getId());
        response.setSnapshot(true);
        response.setReportName(buildReportName(response.getGeneratedAt()));

        entity.setSummarySnapshot(writeSummarySnapshot(response));
        reportRepository.save(entity);

        return response;
    }

    @Override
    @Transactional
    public StudentLearningReportResponse generateLearningReport(Long studentId, GenerateStudentReportRequest request) {
        String requestedRange = request != null ? request.getRange() : DEFAULT_RANGE;
        return createSnapshot(studentId, requestedRange);
    }

    @Override
    @Transactional
    public StudentLearningReportResponse generateQuickReport(Long studentId) {
        return createSnapshot(studentId, DEFAULT_RANGE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentLearningReportResponse> getReportHistory(Long studentId) {
        return reportRepository.findByStudentIdOrderByGeneratedAtDescIdDesc(studentId).stream()
                .map(report -> toSnapshotResponse(report, DEFAULT_RANGE))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentLearningReportResponse> getReportHistory(Long studentId, int page, int size) {
        return reportRepository.findByStudentIdOrderByGeneratedAtDescIdDesc(studentId, PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(report -> toSnapshotResponse(report, DEFAULT_RANGE))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse getLatestReport(Long studentId) {
        return reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(studentId)
                .map(report -> toSnapshotResponse(report, DEFAULT_RANGE))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse getReportById(Long studentId, Long reportId) {
        return toSnapshotResponse(getOwnedReport(studentId, reportId), DEFAULT_RANGE);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse.StudentMetrics getCurrentMetrics(Long studentId) {
        return getSummary(studentId, DEFAULT_RANGE).getMetrics();
    }

    @Override
    public boolean canGenerateNewReport(Long studentId) {
        return true;
    }

    @Override
    public int getCooldownRemainingMinutes(Long studentId) {
        return 0;
    }

    @Override
    public long countReports(Long studentId) {
        return reportRepository.countByStudentId(studentId);
    }

    private User getStudent(Long studentId) {
        return userRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Student not found"));
    }

    private StudentLearningReport getOwnedReport(Long studentId, Long reportId) {
        StudentLearningReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Report not found"));

        Long ownerId = report.getStudent() != null ? report.getStudent().getId() : null;
        if (!Objects.equals(ownerId, studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Không có quyền xem báo cáo này");
        }
        return report;
    }

    private StudentLearningReportResponse buildLiveResponse(User student, String range, boolean snapshot) {
        LocalDateTime generatedAt = LocalDateTime.now(REPORT_ZONE);
        Long studentId = student.getId();
        String studentName = resolveStudentName(student);

        List<RoadmapSession> roadmaps = defaultList(roadmapSessionRepository.findByUserIdAndStatusNotDeleted(studentId));
        List<StudySession> studySessions = safeList(() -> studySessionRepository.findByUserId(studentId));
        List<Task> tasks = safeList(() -> taskRepository.findByUserId(studentId)).stream()
                .filter(task -> task.getArchived() == null || !task.getArchived())
                .collect(Collectors.toList());
        List<CourseEnrollment> enrollments = safeList(() -> courseEnrollmentRepository.findByUserId(studentId, Pageable.unpaged()).getContent());
        List<ShortTermJobApplication> jobApplications = safeList(() -> jobApplicationRepository.findByUserIdOrderByAppliedAtDesc(studentId));

        RoadmapComputation roadmapComputation = computeRoadmapData(roadmaps);
        StudyComputation studyComputation = computeStudyData(studySessions, generatedAt);
        TaskComputation taskComputation = computeTaskData(tasks, generatedAt);
        CourseComputation courseComputation = computeCourseData(enrollments);
        JobComputation jobComputation = computeJobData(jobApplications);

        Integer overallProgress = computeOverallProgressV2(
                roadmapComputation.stats.getRoadmapProgress(),
                roadmapComputation.stats.getTotalMissions(),
                taskComputation.stats.getTaskProgress(),
                taskComputation.stats.getTotalTasks(),
                courseComputation.stats.getAverageActiveCourseProgress(),
                courseComputation.stats.getActiveCourses(),
                jobComputation.stats.getCompletedJobs(),
                jobComputation.stats.getTotalJobsApplied());

        String trend = computeLearningTrendV2(studentId, overallProgress, jobComputation.stats);
        List<StudentLearningReportResponse.Recommendation> recommendations = buildAlgorithmicRecommendations(
                studentId,
                generatedAt,
                studyComputation.stats,
                roadmapComputation.stats,
                roadmapComputation.breakdown,
                taskComputation.stats,
                courseComputation.stats,
                courseComputation.breakdown,
                jobComputation.stats,
                jobComputation.breakdown);

        Map<String, List<StudentLearningReportResponse.TimelinePoint>> timelineByRange =
                buildEnhancedTimelineByRange(studySessions, roadmapComputation.completedMissionInstants,
                        taskComputation.completedTaskInstants, jobComputation.completedJobInstants, generatedAt.toLocalDate());

        StudentLearningReportResponse response = StudentLearningReportResponse.builder()
                .reportName(buildReportName(generatedAt))
                .generatedAt(generatedAt)
                .studentId(studentId)
                .studentName(studentName)
                .reportType(StudentLearningReport.ReportType.COMPREHENSIVE.name())
                .range(range)
                .snapshot(snapshot)
                .overview(StudentLearningReportResponse.Overview.builder()
                        .overallProgress(overallProgress)
                        .learningTrend(trend)
                        .recommendations(recommendations)
                        .build())
                .studyStats(studyComputation.stats)
                .roadmapStats(roadmapComputation.stats)
                .taskStats(taskComputation.stats)
                .courseStats(courseComputation.stats)
                .jobStats(jobComputation.stats)
                .roadmapBreakdown(roadmapComputation.breakdown)
                .courseBreakdown(courseComputation.breakdown)
                .jobBreakdown(jobComputation.breakdown)
                .timelineByRange(timelineByRange)
                .timeline(defaultList(timelineByRange.get(range)))
                .build();

        return finalizeResponse(response);
    }

    private RoadmapComputation computeRoadmapData(List<RoadmapSession> roadmaps) {
        List<StudentLearningReportResponse.RoadmapBreakdownItem> breakdown = new ArrayList<>();
        List<Instant> completedMissionInstants = new ArrayList<>();

        int totalRoadmaps = roadmaps.size();
        int completedRoadmaps = 0;
        int totalMissions = 0;
        int completedMissions = 0;

        for (RoadmapSession roadmap : roadmaps) {
            Map<String, UserRoadmapProgress> progressByQuestId = defaultList(roadmap.getProgressList()).stream()
                    .filter(progress -> progress.getQuestId() != null)
                    .collect(Collectors.toMap(
                            UserRoadmapProgress::getQuestId,
                            progress -> progress,
                            this::preferMoreAdvancedProgress,
                            LinkedHashMap::new));

            Set<String> completedQuestIds = progressByQuestId.values().stream()
                    .filter(progress -> progress.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                    .map(UserRoadmapProgress::getQuestId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<RoadmapNodeSummary> nodes = parseRoadmapNodes(roadmap.getRoadmapJson());
            int totalForRoadmap = Math.max(
                    Math.max(nullSafeInt(roadmap.getTotalNodes()), nodes.size()),
                    progressByQuestId.size());
            int completedForRoadmap = Math.min(completedQuestIds.size(), totalForRoadmap);
            int pendingForRoadmap = Math.max(0, totalForRoadmap - completedForRoadmap);
            int progressPercent = percent(completedForRoadmap, totalForRoadmap);

            totalMissions += totalForRoadmap;
            completedMissions += completedForRoadmap;

            if (totalForRoadmap > 0 && completedForRoadmap >= totalForRoadmap) {
                completedRoadmaps++;
            }

            String nextMissionTitle = nodes.stream()
                    .filter(node -> !completedQuestIds.contains(node.id))
                    .map(node -> node.title)
                    .filter(title -> title != null && !title.isBlank())
                    .findFirst()
                    .orElse(null);

            LocalDateTime lastCompletedAt = progressByQuestId.values().stream()
                    .map(UserRoadmapProgress::getCompletedAt)
                    .filter(Objects::nonNull)
                    .peek(completedMissionInstants::add)
                    .map(this::toLocalDateTime)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            breakdown.add(StudentLearningReportResponse.RoadmapBreakdownItem.builder()
                    .roadmapId(roadmap.getId())
                    .title(defaultIfBlank(roadmap.getTitle(), "Roadmap"))
                    .goal(defaultIfBlank(roadmap.getValidatedGoal(), roadmap.getOriginalGoal()))
                    .status(resolveRoadmapStatus(progressPercent, totalForRoadmap, completedForRoadmap))
                    .totalMissions(totalForRoadmap)
                    .completedMissions(completedForRoadmap)
                    .pendingMissions(pendingForRoadmap)
                    .progressPercent(progressPercent)
                    .nextMissionTitle(nextMissionTitle)
                    .lastCompletedAt(lastCompletedAt)
                    .build());
        }

        breakdown.sort(Comparator
                .comparing(StudentLearningReportResponse.RoadmapBreakdownItem::getProgressPercent)
                .thenComparing(StudentLearningReportResponse.RoadmapBreakdownItem::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)));

        int inProgressRoadmaps = Math.max(0, totalRoadmaps - completedRoadmaps);

        StudentLearningReportResponse.RoadmapStats stats = StudentLearningReportResponse.RoadmapStats.builder()
                .totalRoadmaps(totalRoadmaps)
                .completedRoadmaps(completedRoadmaps)
                .inProgressRoadmaps(inProgressRoadmaps)
                .totalMissions(totalMissions)
                .completedMissions(completedMissions)
                .pendingMissions(Math.max(0, totalMissions - completedMissions))
                .roadmapProgress(percent(completedMissions, totalMissions))
                .build();

        return new RoadmapComputation(stats, breakdown, completedMissionInstants);
    }

    private StudyComputation computeStudyData(List<StudySession> studySessions, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        int studyMinutesToday = sumSessionMinutesWithinRange(studySessions, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int studyMinutesWeek = sumSessionMinutesWithinRange(studySessions, weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int studyMinutesMonth = sumSessionMinutesWithinRange(studySessions, monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int totalStudyMinutes = sumSessionMinutesWithinRange(studySessions, LocalDate.of(2000, 1, 1).atStartOfDay(), now.plusSeconds(1));

        StudentLearningReportResponse.StudyStats stats = StudentLearningReportResponse.StudyStats.builder()
                .studyMinutesToday(studyMinutesToday)
                .studyMinutesWeek(studyMinutesWeek)
                .studyMinutesMonth(studyMinutesMonth)
                .totalStudyHours(Math.round(totalStudyMinutes / 60.0f))
                .currentStreak(calculateCurrentStreak(studySessions, today))
                .build();

        return new StudyComputation(stats);
    }

    private TaskComputation computeTaskData(List<Task> tasks, LocalDateTime now) {
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(this::isTaskCompleted).count();
        int pendingTasks = Math.max(0, totalTasks - completedTasks);
        int overdueTasks = (int) tasks.stream()
                .filter(task -> !isTaskCompleted(task))
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(now))
                .count();

        // Collect completed task timestamps (use endDate as completion time)
        List<Instant> completedTaskInstants = tasks.stream()
                .filter(this::isTaskCompleted)
                .map(Task::getEndDate)
                .filter(Objects::nonNull)
                .map(endDate -> endDate.atZone(REPORT_ZONE).toInstant())
                .collect(Collectors.toList());

        StudentLearningReportResponse.TaskStats stats = StudentLearningReportResponse.TaskStats.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .overdueTasks(overdueTasks)
                .taskProgress(percent(completedTasks, totalTasks))
                .build();

        return new TaskComputation(stats, completedTaskInstants);
    }

    private CourseComputation computeCourseData(List<CourseEnrollment> enrollments) {
        List<CourseEnrollment> activeCourses = enrollments.stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ENROLLED)
                .collect(Collectors.toList());
        List<CourseEnrollment> completedCourses = enrollments.stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.COMPLETED)
                .collect(Collectors.toList());

        int averageActiveCourseProgress = activeCourses.isEmpty()
                ? 0
                : (int) Math.round(activeCourses.stream()
                        .map(CourseEnrollment::getProgressPercent)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0));

        List<StudentLearningReportResponse.CourseBreakdownItem> breakdown = enrollments.stream()
                .filter(enrollment -> enrollment.getStatus() != EnrollmentStatus.DROPPED)
                .map(enrollment -> StudentLearningReportResponse.CourseBreakdownItem.builder()
                        .courseId(enrollment.getCourse() != null ? enrollment.getCourse().getId() : null)
                        .courseTitle(enrollment.getCourse() != null ? enrollment.getCourse().getTitle() : "Course")
                        .status(enrollment.getStatus() != null ? enrollment.getStatus().name().toLowerCase() : "unknown")
                        .progressPercent(nullSafeInt(enrollment.getProgressPercent()))
                        .completedAt(toLocalDateTime(enrollment.getCompletedAt()))
                        .enrolledAt(toLocalDateTime(enrollment.getEnrollDate()))
                        .build())
                .sorted(Comparator
                        .comparing((StudentLearningReportResponse.CourseBreakdownItem item) -> "enrolled".equals(item.getStatus()) ? 0 : 1)
                        .thenComparing(StudentLearningReportResponse.CourseBreakdownItem::getProgressPercent)
                        .thenComparing(StudentLearningReportResponse.CourseBreakdownItem::getCourseTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        StudentLearningReportResponse.CourseStats stats = StudentLearningReportResponse.CourseStats.builder()
                .activeCourses(activeCourses.size())
                .completedCourses(completedCourses.size())
                .averageActiveCourseProgress(averageActiveCourseProgress)
                .build();

        return new CourseComputation(stats, breakdown);
    }

    private Integer computeOverallProgress(
            Integer roadmapProgress,
            Integer totalMissions,
            Integer taskProgress,
            Integer totalTasks,
            Integer courseProgress,
            Integer activeCourses) {
        List<Integer> components = new ArrayList<>();
        if (nullSafeInt(totalMissions) > 0) {
            components.add(nullSafeInt(roadmapProgress));
        }
        if (nullSafeInt(totalTasks) > 0) {
            components.add(nullSafeInt(taskProgress));
        }
        if (nullSafeInt(activeCourses) > 0) {
            components.add(nullSafeInt(courseProgress));
        }
        if (components.isEmpty()) {
            return 0;
        }
        return (int) Math.round(components.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private String computeLearningTrend(Long studentId, Integer currentOverallProgress) {
        Optional<StudentLearningReport> latestSnapshot = reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(studentId);
        if (latestSnapshot.isEmpty()) {
            return "stable";
        }

        Integer previousOverall = extractSnapshotOverallProgress(latestSnapshot.get());
        if (previousOverall == null) {
            return "stable";
        }

        int diff = nullSafeInt(currentOverallProgress) - previousOverall;
        if (diff > 5) {
            return "improving";
        }
        if (diff < -5) {
            return "declining";
        }
        return "stable";
    }

    private List<String> buildRecommendations(
            StudentLearningReportResponse.StudyStats studyStats,
            StudentLearningReportResponse.RoadmapStats roadmapStats,
            List<StudentLearningReportResponse.RoadmapBreakdownItem> roadmapBreakdown,
            StudentLearningReportResponse.TaskStats taskStats,
            StudentLearningReportResponse.CourseStats courseStats,
            List<StudentLearningReportResponse.CourseBreakdownItem> courseBreakdown) {
        List<String> recommendations = new ArrayList<>();

        if (nullSafeInt(studyStats.getCurrentStreak()) == 0 || nullSafeInt(studyStats.getStudyMinutesWeek()) < 120) {
            recommendations.add("Khôi phục nhịp học đều: nhắm 20-30 phút mỗi ngày để vượt 120 phút/tuần.");
        }

        if (nullSafeInt(roadmapStats.getRoadmapProgress()) < 50) {
            roadmapBreakdown.stream()
                    .filter(item -> nullSafeInt(item.getPendingMissions()) > 0)
                    .min(Comparator.comparing(StudentLearningReportResponse.RoadmapBreakdownItem::getProgressPercent))
                    .ifPresent(item -> recommendations.add("Ưu tiên roadmap \"" + item.getTitle()
                            + "\"" + buildMissionSuffix(item.getNextMissionTitle()) + "."));
        }

        if (nullSafeInt(taskStats.getOverdueTasks()) > 0 || nullSafeInt(taskStats.getPendingTasks()) >= 5) {
            recommendations.add("Dọn task tồn: " + nullSafeInt(taskStats.getOverdueTasks())
                    + " quá hạn, " + nullSafeInt(taskStats.getPendingTasks()) + " đang chờ.");
        }

        if (nullSafeInt(courseStats.getAverageActiveCourseProgress()) < 40) {
            courseBreakdown.stream()
                    .filter(item -> "enrolled".equals(item.getStatus()))
                    .min(Comparator.comparing(StudentLearningReportResponse.CourseBreakdownItem::getProgressPercent))
                    .ifPresent(item -> recommendations.add("Đẩy khóa \"" + item.getCourseTitle()
                            + "\" từ " + nullSafeInt(item.getProgressPercent()) + "% lên mốc tiếp theo."));
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Nhịp học đang ổn, giữ streak và hoàn thành mission kế tiếp.");
        }

        return recommendations.stream().limit(3).collect(Collectors.toList());
    }

    private Map<String, List<StudentLearningReportResponse.TimelinePoint>> buildTimelineByRange(
            List<StudySession> studySessions,
            List<Instant> completedMissionInstants,
            LocalDate anchorDate) {
        Map<String, List<StudentLearningReportResponse.TimelinePoint>> timelineByRange = new LinkedHashMap<>();
        timelineByRange.put("7d", buildTimeline("7d", studySessions, completedMissionInstants, anchorDate));
        timelineByRange.put("30d", buildTimeline("30d", studySessions, completedMissionInstants, anchorDate));
        timelineByRange.put("90d", buildTimeline("90d", studySessions, completedMissionInstants, anchorDate));
        return timelineByRange;
    }

    private List<StudentLearningReportResponse.TimelinePoint> buildTimeline(
            String range,
            List<StudySession> studySessions,
            List<Instant> completedMissionInstants,
            LocalDate anchorDate) {
        List<Bucket> buckets = buildBuckets(range, anchorDate);
        List<StudentLearningReportResponse.TimelinePoint> points = new ArrayList<>();

        for (Bucket bucket : buckets) {
            int studyMinutes = sumSessionMinutesWithinRange(
                    studySessions,
                    bucket.start.atStartOfDay(),
                    bucket.endExclusive.atStartOfDay());

            int missionsCompleted = (int) completedMissionInstants.stream()
                    .map(this::toLocalDateTime)
                    .filter(Objects::nonNull)
                    .filter(completedAt -> !completedAt.isBefore(bucket.start.atStartOfDay())
                            && completedAt.isBefore(bucket.endExclusive.atStartOfDay()))
                    .count();

            points.add(StudentLearningReportResponse.TimelinePoint.builder()
                    .bucketLabel(bucket.label)
                    .bucketStart(bucket.start)
                    .studyMinutes(studyMinutes)
                    .missionsCompleted(missionsCompleted)
                    .build());
        }

        return points;
    }

    private List<Bucket> buildBuckets(String range, LocalDate anchorDate) {
        List<Bucket> buckets = new ArrayList<>();
        if ("90d".equals(range)) {
            LocalDate currentWeekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate firstWeekStart = currentWeekStart.minusWeeks(12);
            for (int i = 0; i < 13; i++) {
                LocalDate start = firstWeekStart.plusWeeks(i);
                LocalDate endExclusive = start.plusWeeks(1);
                String label = DATE_LABEL_FORMATTER.format(start) + " - "
                        + DATE_LABEL_FORMATTER.format(endExclusive.minusDays(1));
                buckets.add(new Bucket(start, endExclusive, label));
            }
            return buckets;
        }

        int days = "7d".equals(range) ? 7 : 30;
        LocalDate firstDate = anchorDate.minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate start = firstDate.plusDays(i);
            buckets.add(new Bucket(start, start.plusDays(1), DATE_LABEL_FORMATTER.format(start)));
        }
        return buckets;
    }

    private int sumSessionMinutesWithinRange(List<StudySession> studySessions, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return studySessions.stream()
                .mapToInt(session -> overlapMinutes(session.getStartTime(), session.getEndTime(), rangeStart, rangeEnd))
                .sum();
    }

    private int overlapMinutes(LocalDateTime sessionStart, LocalDateTime sessionEnd, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (sessionStart == null || sessionEnd == null || !sessionEnd.isAfter(sessionStart)) {
            return 0;
        }

        LocalDateTime effectiveStart = sessionStart.isAfter(rangeStart) ? sessionStart : rangeStart;
        LocalDateTime effectiveEnd = sessionEnd.isBefore(rangeEnd) ? sessionEnd : rangeEnd;
        if (!effectiveEnd.isAfter(effectiveStart)) {
            return 0;
        }

        return (int) ChronoUnit.MINUTES.between(effectiveStart, effectiveEnd);
    }

    private int calculateCurrentStreak(List<StudySession> studySessions, LocalDate today) {
        Set<LocalDate> studyDates = studySessions.stream()
                .filter(session -> overlapMinutes(
                        session.getStartTime(),
                        session.getEndTime(),
                        session.getStartTime() != null ? session.getStartTime() : LocalDateTime.MIN,
                        session.getEndTime() != null ? session.getEndTime() : LocalDateTime.MIN) > 0)
                .map(StudySession::getStartTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());

        if (!studyDates.contains(today)) {
            return 0;
        }

        int streak = 0;
        LocalDate cursor = today;
        while (studyDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private List<RoadmapNodeSummary> parseRoadmapNodes(String roadmapJson) {
        if (roadmapJson == null || roadmapJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(roadmapJson);
            JsonNode roadmapArray = root.isArray() ? root : root.path("roadmap");
            if (!roadmapArray.isArray()) {
                return List.of();
            }

            List<RoadmapNodeSummary> nodes = new ArrayList<>();
            for (JsonNode node : roadmapArray) {
                String id = node.path("id").asText(null);
                String title = node.path("title").asText(null);
                if (id != null && !id.isBlank()) {
                    nodes.add(new RoadmapNodeSummary(id, defaultIfBlank(title, id)));
                }
            }
            return nodes;
        } catch (Exception ex) {
            log.warn("Failed to parse roadmap json: {}", ex.getMessage());
            return List.of();
        }
    }

    private StudentLearningReportResponse toSnapshotResponse(StudentLearningReport report, String requestedRange) {
        StudentLearningReportResponse response = readSummarySnapshot(report)
                .orElseGet(() -> buildLegacySnapshotFallback(report));

        response.setId(report.getId());
        response.setReportId(report.getId());
        response.setGeneratedAt(report.getGeneratedAt());
        response.setReportName(buildReportName(report.getGeneratedAt()));
        response.setStudentId(report.getStudent() != null ? report.getStudent().getId() : null);
        response.setStudentName(defaultIfBlank(response.getStudentName(), report.getStudentName()));
        response.setReportType(StudentLearningReport.ReportType.COMPREHENSIVE.name());
        response.setSnapshot(true);
        response.setRange(requestedRange);

        Map<String, List<StudentLearningReportResponse.TimelinePoint>> timelineByRange =
                response.getTimelineByRange() != null ? response.getTimelineByRange() : Map.of();
        if (timelineByRange.containsKey(requestedRange)) {
            response.setTimeline(defaultList(timelineByRange.get(requestedRange)));
        } else if (response.getTimeline() == null) {
            response.setTimeline(List.of());
        }

        return finalizeResponse(response);
    }

    private Optional<StudentLearningReportResponse> readSummarySnapshot(StudentLearningReport report) {
        if (report.getSummarySnapshot() == null || report.getSummarySnapshot().isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(report.getSummarySnapshot(), StudentLearningReportResponse.class));
        } catch (Exception ex) {
            log.warn("Failed to parse summary_snapshot for report {}: {}", report.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private StudentLearningReportResponse buildLegacySnapshotFallback(StudentLearningReport report) {
        String focus = report.getRecommendedFocus();
        StudentLearningReportResponse.Recommendation legacy = StudentLearningReportResponse.Recommendation.builder()
                .id("legacy-snapshot")
                .tier("IMPROVE")
                .category("GROWTH")
                .title(focus == null || focus.isBlank() ? "Snapshot cũ chưa có breakdown chi tiết." : focus)
                .build();
        List<StudentLearningReportResponse.Recommendation> recommendations = List.of(legacy);

        StudentLearningReportResponse response = StudentLearningReportResponse.builder()
                .overview(StudentLearningReportResponse.Overview.builder()
                        .overallProgress(nullSafeInt(report.getAverageProgressSnapshot()))
                        .learningTrend(defaultIfBlank(report.getLearningTrend(), "stable"))
                        .recommendations(recommendations)
                        .build())
                .studyStats(StudentLearningReportResponse.StudyStats.builder()
                        .studyMinutesToday(0)
                        .studyMinutesWeek(0)
                        .studyMinutesMonth(0)
                        .totalStudyHours(nullSafeInt(report.getTotalStudyHoursSnapshot()))
                        .currentStreak(nullSafeInt(report.getStreakDaysSnapshot()))
                        .build())
                .roadmapStats(StudentLearningReportResponse.RoadmapStats.builder()
                        .totalRoadmaps(0)
                        .completedRoadmaps(0)
                        .inProgressRoadmaps(0)
                        .totalMissions(0)
                        .completedMissions(0)
                        .pendingMissions(0)
                        .roadmapProgress(0)
                        .build())
                .taskStats(StudentLearningReportResponse.TaskStats.builder()
                        .totalTasks(nullSafeInt(report.getTasksCompletedSnapshot()))
                        .completedTasks(nullSafeInt(report.getTasksCompletedSnapshot()))
                        .pendingTasks(0)
                        .overdueTasks(0)
                        .taskProgress(100)
                        .build())
                .courseStats(StudentLearningReportResponse.CourseStats.builder()
                        .activeCourses(0)
                        .completedCourses(0)
                        .averageActiveCourseProgress(0)
                        .build())
                .roadmapBreakdown(List.of())
                .courseBreakdown(List.of())
                .timeline(List.of())
                .timelineByRange(Map.of())
                .build();

        return finalizeResponse(response);
    }

    private StudentLearningReportResponse finalizeResponse(StudentLearningReportResponse response) {
        if (response.getReportId() == null) {
            response.setReportId(response.getId());
        }
        if (response.getReportType() == null) {
            response.setReportType(StudentLearningReport.ReportType.COMPREHENSIVE.name());
        }
        if (response.getRange() == null) {
            response.setRange(DEFAULT_RANGE);
        }
        if (response.getReportName() == null && response.getGeneratedAt() != null) {
            response.setReportName(buildReportName(response.getGeneratedAt()));
        }
        if (response.getTimeline() == null) {
            response.setTimeline(List.of());
        }
        if (response.getRoadmapBreakdown() == null) {
            response.setRoadmapBreakdown(List.of());
        }
        if (response.getCourseBreakdown() == null) {
            response.setCourseBreakdown(List.of());
        }
        if (response.getTimelineByRange() == null) {
            response.setTimelineByRange(Map.of());
        }
        if (response.getOverview() == null) {
            response.setOverview(StudentLearningReportResponse.Overview.builder()
                    .overallProgress(0)
                    .learningTrend("stable")
                    .recommendations(List.of(StudentLearningReportResponse.Recommendation.builder()
                            .id("empty-data")
                            .tier("IMPROVE")
                            .category("GROWTH")
                            .title("Chưa có dữ liệu để đưa ra khuyến nghị.")
                            .action("Bắt đầu phiên học hoặc apply roadmap đầu tiên để hệ thống phân tích.")
                            .build()))
                    .build());
        }

        response.setOverallProgress(getOverallProgress(response));
        response.setLearningTrend(getLearningTrend(response));
        response.setRecommendedFocus(getRecommendedFocus(response));
        response.setMetrics(buildCompatibilityMetrics(response));
        response.setReportContent(null);
        response.setSections(null);
        return response;
    }

    private StudentLearningReportResponse.StudentMetrics buildCompatibilityMetrics(StudentLearningReportResponse response) {
        List<StudentLearningReportResponse.RoadmapProgress> roadmapDetails = defaultList(response.getRoadmapBreakdown()).stream()
                .map(item -> StudentLearningReportResponse.RoadmapProgress.builder()
                        .roadmapId(item.getRoadmapId())
                        .title(item.getTitle())
                        .goal(item.getGoal())
                        .totalQuests(item.getTotalMissions())
                        .completedQuests(item.getCompletedMissions())
                        .progressPercent(item.getProgressPercent())
                        .lastActivityAt(item.getLastCompletedAt())
                        .build())
                .collect(Collectors.toList());

        return StudentLearningReportResponse.StudentMetrics.builder()
                .totalRoadmaps(response.getRoadmapStats() != null ? response.getRoadmapStats().getTotalRoadmaps() : 0)
                .completedRoadmaps(response.getRoadmapStats() != null ? response.getRoadmapStats().getCompletedRoadmaps() : 0)
                .inProgressRoadmaps(response.getRoadmapStats() != null ? response.getRoadmapStats().getInProgressRoadmaps() : 0)
                .averageProgress(getOverallProgress(response))
                .totalStudyMinutesToday(response.getStudyStats() != null ? response.getStudyStats().getStudyMinutesToday() : 0)
                .totalStudyMinutesWeek(response.getStudyStats() != null ? response.getStudyStats().getStudyMinutesWeek() : 0)
                .totalStudyMinutesMonth(response.getStudyStats() != null ? response.getStudyStats().getStudyMinutesMonth() : 0)
                .totalStudyHours(response.getStudyStats() != null ? response.getStudyStats().getTotalStudyHours() : 0)
                .streakDays(response.getStudyStats() != null ? response.getStudyStats().getCurrentStreak() : 0)
                .currentStreak(response.getStudyStats() != null ? response.getStudyStats().getCurrentStreak() : 0)
                .totalChatSessions(0)
                .totalTasks(response.getTaskStats() != null ? response.getTaskStats().getTotalTasks() : 0)
                .completedTasks(response.getTaskStats() != null ? response.getTaskStats().getCompletedTasks() : 0)
                .totalTasksCompleted(response.getTaskStats() != null ? response.getTaskStats().getCompletedTasks() : 0)
                .totalTasksPending(response.getTaskStats() != null ? response.getTaskStats().getPendingTasks() : 0)
                .totalEnrolledCourses(response.getCourseStats() != null
                        ? nullSafeInt(response.getCourseStats().getActiveCourses()) + nullSafeInt(response.getCourseStats().getCompletedCourses())
                        : 0)
                .completedCourses(response.getCourseStats() != null ? response.getCourseStats().getCompletedCourses() : 0)
                .topSkills(List.of())
                .roadmapDetails(roadmapDetails)
                .build();
    }

    private Integer extractSnapshotOverallProgress(StudentLearningReport report) {
        Optional<StudentLearningReportResponse> summarySnapshot = readSummarySnapshot(report);
        if (summarySnapshot.isPresent() && summarySnapshot.get().getOverview() != null) {
            return summarySnapshot.get().getOverview().getOverallProgress();
        }
        return report.getAverageProgressSnapshot();
    }

    private String writeSummarySnapshot(StudentLearningReportResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to persist learning report snapshot");
        }
    }

    private String resolveStudentName(User student) {
        if (student == null) {
            return "Học viên";
        }
        if (student.getFullName() != null && !student.getFullName().trim().isEmpty()) {
            return student.getFullName().trim();
        }
        String combined = ((student.getFirstName() != null ? student.getFirstName() : "")
                + " "
                + (student.getLastName() != null ? student.getLastName() : "")).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        if (student.getEmail() != null && student.getEmail().contains("@")) {
            return student.getEmail().split("@")[0];
        }
        return "Học viên";
    }

    private String normalizeRange(String range) {
        String normalized = range == null ? DEFAULT_RANGE : range.trim().toLowerCase();
        if (!SUPPORTED_RANGES.contains(normalized)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Range phải là 7d, 30d hoặc 90d");
        }
        return normalized;
    }

    private String buildReportName(LocalDateTime generatedAt) {
        LocalDateTime safeGeneratedAt = generatedAt != null ? generatedAt : LocalDateTime.now(REPORT_ZONE);
        return "Báo cáo " + safeGeneratedAt.format(REPORT_NAME_FORMATTER);
    }

    private String getLearningTrend(StudentLearningReportResponse response) {
        return response.getOverview() != null && response.getOverview().getLearningTrend() != null
                ? response.getOverview().getLearningTrend()
                : "stable";
    }

    private Integer getOverallProgress(StudentLearningReportResponse response) {
        return response.getOverview() != null ? nullSafeInt(response.getOverview().getOverallProgress()) : 0;
    }

    private String getRecommendedFocus(StudentLearningReportResponse response) {
        if (response.getOverview() == null
                || response.getOverview().getRecommendations() == null
                || response.getOverview().getRecommendations().isEmpty()) {
            return null;
        }
        StudentLearningReportResponse.Recommendation first = response.getOverview().getRecommendations().get(0);
        if (first == null) {
            return null;
        }
        return first.getTitle() != null ? first.getTitle() : first.getAction();
    }

    private boolean isTaskCompleted(Task task) {
        if (task == null) {
            return false;
        }
        return "DONE".equalsIgnoreCase(task.getStatus()) || nullSafeInt(task.getUserProgress()) >= 100;
    }

    private UserRoadmapProgress preferMoreAdvancedProgress(UserRoadmapProgress left, UserRoadmapProgress right) {
        int leftScore = progressRank(left != null ? left.getStatus() : null);
        int rightScore = progressRank(right != null ? right.getStatus() : null);
        if (rightScore > leftScore) {
            return right;
        }
        if (leftScore > rightScore) {
            return left;
        }

        Instant leftCompletedAt = left != null ? left.getCompletedAt() : null;
        Instant rightCompletedAt = right != null ? right.getCompletedAt() : null;
        if (rightCompletedAt != null && (leftCompletedAt == null || rightCompletedAt.isAfter(leftCompletedAt))) {
            return right;
        }
        return left;
    }

    private int progressRank(UserRoadmapProgress.ProgressStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case COMPLETED -> 3;
            case IN_PROGRESS -> 2;
            case SKIPPED -> 1;
            case NOT_STARTED -> 0;
        };
    }

    private String resolveRoadmapStatus(int progressPercent, int totalMissions, int completedMissions) {
        if (totalMissions > 0 && completedMissions >= totalMissions) {
            return "completed";
        }
        if (progressPercent > 0) {
            return "in_progress";
        }
        return "not_started";
    }

    private int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, REPORT_ZONE);
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultIfBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private String buildMissionSuffix(String nextMissionTitle) {
        if (nextMissionTitle == null || nextMissionTitle.isBlank()) {
            return " và hoàn thành mission kế tiếp";
        }
        return ": hoàn thành \"" + nextMissionTitle + "\"";
    }

    private <T> List<T> defaultList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private <T> List<T> safeList(SupplierWithException<List<T>> supplier) {
        try {
            List<T> values = supplier.get();
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            log.warn("Failed to load learning report dependency: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record Bucket(LocalDate start, LocalDate endExclusive, String label) {
    }

    private record RoadmapNodeSummary(String id, String title) {
    }

    private record RoadmapComputation(
            StudentLearningReportResponse.RoadmapStats stats,
            List<StudentLearningReportResponse.RoadmapBreakdownItem> breakdown,
            List<Instant> completedMissionInstants) {
    }

    private record StudyComputation(StudentLearningReportResponse.StudyStats stats) {
    }

    private record TaskComputation(
            StudentLearningReportResponse.TaskStats stats,
            List<Instant> completedTaskInstants) {
    }

    private record CourseComputation(
            StudentLearningReportResponse.CourseStats stats,
            List<StudentLearningReportResponse.CourseBreakdownItem> breakdown) {
    }

    private record JobComputation(
            StudentLearningReportResponse.ShortTermJobStats stats,
            List<StudentLearningReportResponse.JobBreakdownItem> breakdown,
            List<Instant> completedJobInstants,
            List<JobEarningPoint> earningPoints) {
    }

    private record JobEarningPoint(LocalDateTime timestamp, BigDecimal amount) {
    }

    private JobComputation computeJobData(List<ShortTermJobApplication> applications) {
        List<StudentLearningReportResponse.JobBreakdownItem> breakdown = new ArrayList<>();
        List<Instant> completedJobInstants = new ArrayList<>();
        List<JobEarningPoint> earningPoints = new ArrayList<>();

        int totalApplied = applications.size();
        int completedJobs = 0;
        int inProgressJobs = 0;
        int pendingApps = 0;
        int rejectedApps = 0;
        int totalMilestonesDelivered = 0;
        int onTimeDeliveries = 0;
        BigDecimal totalEarnings = BigDecimal.ZERO;
        List<Double> ratings = new ArrayList<>();

        for (ShortTermJobApplication app : applications) {
            ShortTermJob job = app.getShortTermJob();
            String status = app.getStatus().name();

            // Count by status
            if (app.getStatus() == ShortTermApplicationStatus.COMPLETED ||
                app.getStatus() == ShortTermApplicationStatus.APPROVED) {
                completedJobs++;
                if (app.getCompletedAt() != null) {
                    completedJobInstants.add(app.getCompletedAt().atZone(REPORT_ZONE).toInstant());
                    earningPoints.add(new JobEarningPoint(app.getCompletedAt(), job != null ? job.getBudget() : BigDecimal.ZERO));
                }
                totalEarnings = totalEarnings.add(job != null ? job.getBudget() : BigDecimal.ZERO);
            } else if (app.getStatus() == ShortTermApplicationStatus.WORKING ||
                       app.getStatus() == ShortTermApplicationStatus.ACCEPTED) {
                inProgressJobs++;
            } else if (app.getStatus() == ShortTermApplicationStatus.PENDING) {
                pendingApps++;
            } else if (app.getStatus() == ShortTermApplicationStatus.REJECTED) {
                rejectedApps++;
            }

            // Count deliverables/milestones
            int milestonesDone = app.getDeliverables() != null ?
                (int) app.getDeliverables().stream().filter(d -> d.getUploadedAt() != null).count() : 0;
            int milestonesTotal = job != null && job.getMilestones() != null ? job.getMilestones().size() : 1;
            totalMilestonesDelivered += milestonesDone;

            // Check on-time delivery
            if (app.getCompletedAt() != null && job != null && job.getDeadline() != null) {
                if (!app.getCompletedAt().isAfter(job.getDeadline())) {
                    onTimeDeliveries++;
                }
            }

            // Build breakdown item
            List<String> skills = new ArrayList<>();
            if (job != null && job.getRequiredSkills() != null) {
                try {
                    skills = objectMapper.readValue(job.getRequiredSkills(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }

            Double rating = null;
            String recruiterName = job != null && job.getRecruiterProfile() != null ?
                job.getRecruiterProfile().getCompanyName() : "Unknown";

            breakdown.add(StudentLearningReportResponse.JobBreakdownItem.builder()
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : "Unknown Job")
                .recruiterName(recruiterName)
                .status(status.toLowerCase())
                .budget(job != null ? job.getBudget() : BigDecimal.ZERO)
                .earnedAmount(app.getStatus() == ShortTermApplicationStatus.COMPLETED ||
                              app.getStatus() == ShortTermApplicationStatus.APPROVED ?
                              (job != null ? job.getBudget() : BigDecimal.ZERO) : BigDecimal.ZERO)
                .milestonesTotal(milestonesTotal)
                .milestonesCompleted(milestonesDone)
                .appliedAt(app.getAppliedAt())
                .completedAt(app.getCompletedAt())
                .rating(rating)
                .primarySkill(job != null ? job.getPrimarySkill() : null)
                .skillsDemonstrated(skills)
                .build());
        }

        // Calculate average rating
        Double averageRating = ratings.isEmpty() ? null :
            ratings.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Calculate on-time rate
        int onTimeRate = completedJobs > 0 ? (onTimeDeliveries * 100) / completedJobs : 0;

        StudentLearningReportResponse.ShortTermJobStats stats =
            StudentLearningReportResponse.ShortTermJobStats.builder()
                .totalJobsApplied(totalApplied)
                .completedJobs(completedJobs)
                .inProgressJobs(inProgressJobs)
                .pendingApplications(pendingApps)
                .rejectedApplications(rejectedApps)
                .totalEarnings(totalEarnings)
                .averageRating(averageRating)
                .totalMilestonesDelivered(totalMilestonesDelivered)
                .onTimeDeliveryRate(onTimeRate)
                .build();

        return new JobComputation(stats, breakdown, completedJobInstants, earningPoints);
    }

    private Integer computeOverallProgressV2(
            Integer roadmapProgress,
            Integer totalMissions,
            Integer taskProgress,
            Integer totalTasks,
            Integer courseProgress,
            Integer activeCourses,
            Integer completedJobs,
            Integer totalJobsApplied) {
        List<Integer> components = new ArrayList<>();
        int weight = 0;

        if (nullSafeInt(totalMissions) > 0) {
            components.add(nullSafeInt(roadmapProgress));
            weight++;
        }
        if (nullSafeInt(totalTasks) > 0) {
            components.add(nullSafeInt(taskProgress));
            weight++;
        }
        if (nullSafeInt(activeCourses) > 0) {
            components.add(nullSafeInt(courseProgress));
            weight++;
        }
        if (nullSafeInt(totalJobsApplied) > 0) {
            int jobSuccessRate = percent(nullSafeInt(completedJobs), nullSafeInt(totalJobsApplied));
            components.add(jobSuccessRate);
            weight++;
        }

        if (components.isEmpty()) {
            return 0;
        }

        // Weighted average: learning activities have higher weight than job applications
        return (int) Math.round(components.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private String computeLearningTrendV2(Long studentId, Integer currentOverallProgress,
                                            StudentLearningReportResponse.ShortTermJobStats jobStats) {
        Optional<StudentLearningReport> latestSnapshot =
            reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(studentId);
        if (latestSnapshot.isEmpty()) {
            // Check job activity as indicator for new users
            if (nullSafeInt(jobStats.getCompletedJobs()) > 0) {
                return "improving";
            }
            return "stable";
        }

        Integer previousOverall = extractSnapshotOverallProgress(latestSnapshot.get());
        if (previousOverall == null) {
            return "stable";
        }

        int diff = nullSafeInt(currentOverallProgress) - previousOverall;

        // Enhanced trend calculation considering job performance
        int jobBoost = 0;
        if (nullSafeInt(jobStats.getCompletedJobs()) > 0) {
            jobBoost = 3; // Small boost for having completed jobs
        }
        if (nullSafeInt(jobStats.getOnTimeDeliveryRate()) > 80) {
            jobBoost += 2; // Additional boost for excellent delivery performance
        }

        int adjustedDiff = diff + jobBoost;

        if (adjustedDiff > 5) {
            return "improving";
        }
        if (adjustedDiff < -5) {
            return "declining";
        }
        return "stable";
    }

    private List<StudentLearningReportResponse.Recommendation> buildAlgorithmicRecommendations(
            Long studentId,
            LocalDateTime now,
            StudentLearningReportResponse.StudyStats studyStats,
            StudentLearningReportResponse.RoadmapStats roadmapStats,
            List<StudentLearningReportResponse.RoadmapBreakdownItem> roadmapBreakdown,
            StudentLearningReportResponse.TaskStats taskStats,
            StudentLearningReportResponse.CourseStats courseStats,
            List<StudentLearningReportResponse.CourseBreakdownItem> courseBreakdown,
            StudentLearningReportResponse.ShortTermJobStats jobStats,
            List<StudentLearningReportResponse.JobBreakdownItem> jobBreakdown) {
        RecommendationEngine.EngineInput input = new RecommendationEngine.EngineInput();
        input.studyStats = studyStats;
        input.roadmapStats = roadmapStats;
        input.roadmapBreakdown = roadmapBreakdown;
        input.taskStats = taskStats;
        input.courseStats = courseStats;
        input.courseBreakdown = courseBreakdown;
        input.jobStats = jobStats;
        input.jobBreakdown = jobBreakdown;
        input.now = now;
        input.previousWeeklyStudyMinutes = readPreviousWeeklyStudyMinutes(studentId);
        return recommendationEngine.generate(input);
    }

    private Integer readPreviousWeeklyStudyMinutes(Long studentId) {
        if (studentId == null) {
            return null;
        }
        return reportRepository.findFirstByStudentIdOrderByGeneratedAtDescIdDesc(studentId)
                .map(StudentLearningReport::getSummarySnapshot)
                .filter(json -> json != null && !json.isBlank())
                .map(json -> {
                    try {
                        StudentLearningReportResponse prev = objectMapper.readValue(json, StudentLearningReportResponse.class);
                        if (prev.getStudyStats() != null) {
                            return prev.getStudyStats().getStudyMinutesWeek();
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to read previous weekly minutes: {}", ex.getMessage());
                    }
                    return null;
                })
                .orElse(null);
    }

    /** @deprecated replaced by {@link RecommendationEngine}. Retained for reference only. */
    @Deprecated
    @SuppressWarnings("unused")
    private List<String> buildEnhancedRecommendationsLegacy(
            StudentLearningReportResponse.StudyStats studyStats,
            StudentLearningReportResponse.RoadmapStats roadmapStats,
            List<StudentLearningReportResponse.RoadmapBreakdownItem> roadmapBreakdown,
            StudentLearningReportResponse.TaskStats taskStats,
            StudentLearningReportResponse.CourseStats courseStats,
            List<StudentLearningReportResponse.CourseBreakdownItem> courseBreakdown,
            StudentLearningReportResponse.ShortTermJobStats jobStats,
            List<StudentLearningReportResponse.JobBreakdownItem> jobBreakdown) {
        List<String> recommendations = new ArrayList<>();
        List<String> detailedAssessments = new ArrayList<>();

        // === STUDY HABITS ANALYSIS ===
        int weeklyMinutes = nullSafeInt(studyStats.getStudyMinutesWeek());
        int streak = nullSafeInt(studyStats.getCurrentStreak());

        if (streak == 0 || weeklyMinutes < 120) {
            recommendations.add("🎯 Khôi phục nhịp học đều đặn: nhắm 20-30 phút mỗi ngày để đạt 120 phút/tuần.");
            detailedAssessments.add("Phân tích: Streak hiện tại là " + streak + " ngày, thời gian học tuần này " +
                weeklyMinutes + " phút. Đề xuất: Thiết lập reminder học tập đều đặn.");
        } else if (weeklyMinutes >= 300) {
            detailedAssessments.add("✅ Phân tích: Thời gian học xuất sắc (" + weeklyMinutes + " phút/tuần). " +
                "Duy trì nhịp độ này để đạt hiệu quả cao nhất.");
        }

        // === ROADMAP PROGRESS ANALYSIS ===
        int roadmapProgress = nullSafeInt(roadmapStats.getRoadmapProgress());
        if (roadmapProgress < 50) {
            roadmapBreakdown.stream()
                .filter(item -> nullSafeInt(item.getPendingMissions()) > 0)
                .min(Comparator.comparing(StudentLearningReportResponse.RoadmapBreakdownItem::getProgressPercent))
                .ifPresent(item -> {
                    recommendations.add("🗺️ Ưu tiên roadmap \"" + item.getTitle() + "\"" +
                        buildMissionSuffix(item.getNextMissionTitle()) + ".");
                    detailedAssessments.add("Phân tích: Roadmap \"" + item.getTitle() + "\" đang ở " +
                        item.getProgressPercent() + "%. Đề xuất: Tập trung hoàn thành mission kế tiếp \"" +
                        (item.getNextMissionTitle() != null ? item.getNextMissionTitle() : "N/A") + "\".");
                });
        } else if (roadmapProgress >= 80) {
            detailedAssessments.add("✅ Phân tích: Tiến độ roadmap xuất sắc (" + roadmapProgress + "%). " +
                "Bạn đang duy trì momentum tốt.");
        }

        // === TASK MANAGEMENT ANALYSIS ===
        int overdueTasks = nullSafeInt(taskStats.getOverdueTasks());
        int pendingTasks = nullSafeInt(taskStats.getPendingTasks());

        if (overdueTasks > 0 || pendingTasks >= 5) {
            recommendations.add("📋 Dọn dẹp task tồn đọng: " + overdueTasks + " quá hạn, " + pendingTasks + " đang chờ.");
            detailedAssessments.add("Phân tích: Có " + overdueTasks + " task quá hạn và " + pendingTasks +
                " task đang chờ. Đề xuất: Sử dụng Eisenhower Matrix để ưu tiên task quan trọng.");
        }

        // === COURSE PROGRESS ANALYSIS ===
        int avgCourseProgress = nullSafeInt(courseStats.getAverageActiveCourseProgress());
        if (avgCourseProgress < 40) {
            courseBreakdown.stream()
                .filter(item -> "enrolled".equals(item.getStatus()))
                .min(Comparator.comparing(StudentLearningReportResponse.CourseBreakdownItem::getProgressPercent))
                .ifPresent(item -> {
                    recommendations.add("📚 Đẩy nhanh khóa \"" + item.getCourseTitle() + "\" từ " +
                        item.getProgressPercent() + "% lên mốc tiếp theo.");
                    detailedAssessments.add("Phân tích: Khóa \"" + item.getCourseTitle() + "\" đang chậm tiến độ. " +
                        "Đề xuất: Phân chia mục tiêu nhỏ, học 30 phút/ngày để cải thiện.");
                });
        }

        // === JOB PERFORMANCE ANALYSIS ===
        int completedJobs = nullSafeInt(jobStats.getCompletedJobs());
        int totalApplied = nullSafeInt(jobStats.getTotalJobsApplied());
        int onTimeRate = nullSafeInt(jobStats.getOnTimeDeliveryRate());

        if (totalApplied == 0) {
            recommendations.add("💼 Bắt đầu apply Short-term Job để tích lũy kinh nghiệm thực tế.");
            detailedAssessments.add("Phân tích: Chưa có job application nào. Đề xuất: Bắt đầu với job nhỏ " +
                "để xây dựng portfolio và rating.");
        } else if (completedJobs == 0 && totalApplied > 0) {
            recommendations.add("🚀 Tập trung hoàn thành job đang làm để xây dựng trust score.");
            detailedAssessments.add("Phân tích: Đã apply " + totalApplied + " job nhưng chưa hoàn thành job nào. " +
                "Đề xuất: Ưu tiên chất lượng delivery để nhận review tốt.");
        } else if (onTimeRate < 70 && completedJobs > 0) {
            recommendations.add("⏰ Cải thiện on-time delivery: Hiện tại " + onTimeRate + "%. Nhắm đến 90%+");
            detailedAssessments.add("Phân tích: Tỷ lệ giao hàng đúng hạn thấp (" + onTimeRate + "%). " +
                "Đề xuất: Sử dụng buffer time, đặt deadline nội bộ trước deadline thực.");
        } else if (completedJobs >= 3 && onTimeRate >= 80) {
            detailedAssessments.add("✅ Phân tích job: Hiệu suất xuất sắc! " + completedJobs +
                " job hoàn thành, on-time rate " + onTimeRate + "%.");
        }

        // Add earnings analysis if applicable
        if (jobStats.getTotalEarnings() != null && jobStats.getTotalEarnings().compareTo(BigDecimal.ZERO) > 0) {
            detailedAssessments.add("💰 Tổng thu nhập từ job: " + jobStats.getTotalEarnings().toString() + " VND.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("🌟 Nhịp học và làm việc đang ổn định. Tiếp tục duy trì và tìm cơ hội nâng cao!");
        }

        // Add all detailed assessments
        recommendations.addAll(detailedAssessments);

        return recommendations.stream().limit(6).collect(Collectors.toList());
    }

    private Map<String, List<StudentLearningReportResponse.TimelinePoint>> buildEnhancedTimelineByRange(
            List<StudySession> studySessions,
            List<Instant> completedMissionInstants,
            List<Instant> completedTaskInstants,
            List<Instant> completedJobInstants,
            LocalDate anchorDate) {
        Map<String, List<StudentLearningReportResponse.TimelinePoint>> timelineByRange = new LinkedHashMap<>();
        timelineByRange.put("7d", buildEnhancedTimeline("7d", studySessions, completedMissionInstants,
            completedTaskInstants, completedJobInstants, anchorDate));
        timelineByRange.put("30d", buildEnhancedTimeline("30d", studySessions, completedMissionInstants,
            completedTaskInstants, completedJobInstants, anchorDate));
        timelineByRange.put("90d", buildEnhancedTimeline("90d", studySessions, completedMissionInstants,
            completedTaskInstants, completedJobInstants, anchorDate));
        return timelineByRange;
    }

    private List<StudentLearningReportResponse.TimelinePoint> buildEnhancedTimeline(
            String range,
            List<StudySession> studySessions,
            List<Instant> completedMissionInstants,
            List<Instant> completedTaskInstants,
            List<Instant> completedJobInstants,
            LocalDate anchorDate) {
        List<Bucket> buckets = buildBuckets(range, anchorDate);
        List<StudentLearningReportResponse.TimelinePoint> points = new ArrayList<>();

        for (Bucket bucket : buckets) {
            LocalDateTime bucketStart = bucket.start.atStartOfDay();
            LocalDateTime bucketEnd = bucket.endExclusive.atStartOfDay();

            // Study minutes
            int studyMinutes = sumSessionMinutesWithinRange(studySessions, bucketStart, bucketEnd);

            // Missions completed in this bucket
            int missionsCompleted = (int) completedMissionInstants.stream()
                .map(this::toLocalDateTime)
                .filter(Objects::nonNull)
                .filter(completedAt -> !completedAt.isBefore(bucketStart) && completedAt.isBefore(bucketEnd))
                .count();

            // Tasks completed in this bucket
            int tasksCompleted = (int) completedTaskInstants.stream()
                .map(this::toLocalDateTime)
                .filter(Objects::nonNull)
                .filter(completedAt -> !completedAt.isBefore(bucketStart) && completedAt.isBefore(bucketEnd))
                .count();

            // Jobs completed in this bucket
            int jobsCompleted = (int) completedJobInstants.stream()
                .map(this::toLocalDateTime)
                .filter(Objects::nonNull)
                .filter(completedAt -> !completedAt.isBefore(bucketStart) && completedAt.isBefore(bucketEnd))
                .count();

            points.add(StudentLearningReportResponse.TimelinePoint.builder()
                .bucketLabel(bucket.label)
                .bucketStart(bucket.start)
                .studyMinutes(studyMinutes)
                .missionsCompleted(missionsCompleted)
                .tasksCompleted(tasksCompleted)
                .jobsCompleted(jobsCompleted)
                .earnings(BigDecimal.ZERO) // Earnings tracking would need more complex calculation
                .build());
        }

        return points;
    }
}
