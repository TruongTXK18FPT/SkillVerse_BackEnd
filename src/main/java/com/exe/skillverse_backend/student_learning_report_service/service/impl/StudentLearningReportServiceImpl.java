package com.exe.skillverse_backend.student_learning_report_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.service.AiChatbotService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.entity.StudentLearningReport;
import com.exe.skillverse_backend.student_learning_report_service.repository.StudentLearningReportRepository;
import com.exe.skillverse_backend.student_learning_report_service.service.StudentLearningReportService;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation của StudentLearningReportService.
 * Sử dụng Mistral AI để phân tích dữ liệu học tập và tạo báo cáo cá nhân.
 */
@Slf4j
@Service
public class StudentLearningReportServiceImpl implements StudentLearningReportService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    
    // Rate limit: 1 báo cáo comprehensive mỗi 6 giờ
    private static final int REPORT_COOLDOWN_HOURS = 6;

    private final StudentLearningReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final StudySessionRepository studySessionRepository;
    private final TaskRepository taskRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final AiChatbotService aiChatbotService;
    private final ChatModel learningReportChatModel;

    @Value("${skillverse.ai.learning-report.enabled:true}")
    private boolean aiEnabled;

    public StudentLearningReportServiceImpl(
            StudentLearningReportRepository reportRepository,
            UserRepository userRepository,
            RoadmapSessionRepository roadmapSessionRepository,
            StudySessionRepository studySessionRepository,
            TaskRepository taskRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            AiChatbotService aiChatbotService,
            @Lazy @Qualifier("learningReportChatModel") ChatModel learningReportChatModel) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.studySessionRepository = studySessionRepository;
        this.taskRepository = taskRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.aiChatbotService = aiChatbotService;
        this.learningReportChatModel = learningReportChatModel;
    }

    @Override
    @Transactional
    public StudentLearningReportResponse generateLearningReport(Long studentId, GenerateStudentReportRequest request) {
        log.info("Generating learning report for student: {}, type: {}", studentId, request.getReportType());

        // Verify student exists
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Student not found"));

        // Check rate limit for comprehensive reports
        if (request.getReportType() == StudentLearningReport.ReportType.COMPREHENSIVE) {
            if (!canGenerateNewReport(studentId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, 
                    "Bạn chỉ có thể tạo báo cáo toàn diện mỗi " + REPORT_COOLDOWN_HOURS + " giờ một lần");
            }
        }

        String studentName = resolveStudentName(student);

        // Gather student data
        StudentLearningReportResponse.StudentMetrics metrics = collectStudentMetrics(studentId);
        List<RoadmapSessionSummary> roadmaps = getRoadmapSummaries(studentId);
        List<ChatSessionSummary> chatSessions = getChatSessionSummaries(studentId);

        // Build context for AI
        String dataContext = buildDataContext(studentName, metrics, roadmaps, chatSessions, request);

        // Get AI prompt based on report type
        String systemPrompt = getSystemPrompt(request.getReportType());
        String userPrompt = "Dựa trên dữ liệu sau, hãy tạo báo cáo học tập chi tiết:\n\n" + dataContext;

        if (request.getPersonalNotes() != null && !request.getPersonalNotes().trim().isEmpty()) {
            userPrompt += "\n\nGhi chú từ học viên: " + request.getPersonalNotes();
        }

        String reportContent;
        StudentLearningReportResponse.ReportSections sections;

        try {
            if (aiEnabled) {
                ChatClient chatClient = ChatClient.create(learningReportChatModel);
                reportContent = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();
                sections = parseSections(reportContent);
            } else {
                // Fallback if AI is disabled
                return generateFallbackReport(studentId, studentName, metrics, roadmaps, request.getReportType());
            }
        } catch (Exception e) {
            log.error("Failed to generate learning report with AI for student {}", studentId, e);
            return generateFallbackReport(studentId, studentName, metrics, roadmaps, request.getReportType());
        }

        // Save report
        try {
            StudentLearningReport savedReport = saveReport(student, studentName, reportContent, sections, 
                    true, request.getReportType());
            log.info("✅ Student learning report saved with ID: {}", savedReport.getId());

            return buildResponse(savedReport, metrics);
        } catch (Exception e) {
            log.error("Failed to persist learning report for student {}", studentId, e);
            // Return generated content without saving
            return StudentLearningReportResponse.builder()
                    .generatedAt(LocalDateTime.now(VN_ZONE))
                    .studentId(studentId)
                    .studentName(studentName)
                    .reportContent(reportContent)
                    .sections(sections)
                    .metrics(metrics)
                    .reportType(request.getReportType().name())
                    .build();
        }
    }

    @Override
    @Transactional
    public StudentLearningReportResponse generateQuickReport(Long studentId) {
        return generateLearningReport(studentId, GenerateStudentReportRequest.builder()
                .reportType(StudentLearningReport.ReportType.COMPREHENSIVE)
                .includeRoadmapDetails(true)
                .includeChatHistory(true)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentLearningReportResponse> getReportHistory(Long studentId) {
        return reportRepository.findByStudentIdOrderByGeneratedAtDesc(studentId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentLearningReportResponse> getReportHistory(Long studentId, int page, int size) {
        return reportRepository.findByStudentIdOrderByGeneratedAtDesc(studentId, PageRequest.of(page, size))
                .getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse getLatestReport(Long studentId) {
        return reportRepository.findFirstByStudentIdOrderByGeneratedAtDesc(studentId)
                .map(this::convertToResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse getReportById(Long studentId, Long reportId) {
        StudentLearningReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Report not found"));
        
        if (!report.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Không có quyền xem báo cáo này");
        }

        return convertToResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningReportResponse.StudentMetrics getCurrentMetrics(Long studentId) {
        // Verify student exists
        if (!userRepository.existsById(studentId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Student not found");
        }
        return collectStudentMetrics(studentId);
    }

    @Override
    public boolean canGenerateNewReport(Long studentId) {
        LocalDateTime cooldownTime = LocalDateTime.now(VN_ZONE).minusHours(REPORT_COOLDOWN_HOURS);
        return !reportRepository.existsByStudentIdAndReportTypeAndGeneratedAtAfter(
                studentId, StudentLearningReport.ReportType.COMPREHENSIVE, cooldownTime);
    }

    @Override
    public long countReports(Long studentId) {
        return reportRepository.countByStudentId(studentId);
    }

    // ============ PRIVATE HELPER METHODS ============

    private StudentLearningReportResponse.StudentMetrics collectStudentMetrics(Long studentId) {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        LocalDateTime startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

        // Roadmap metrics
        List<RoadmapSession> roadmaps = roadmapSessionRepository.findByUserIdOrderByCreatedAtDesc(studentId);
        int totalRoadmaps = roadmaps.size();
        int completedRoadmaps = 0;
        int inProgressRoadmaps = 0;
        int totalProgress = 0;

        List<StudentLearningReportResponse.RoadmapProgress> roadmapDetails = new ArrayList<>();

        for (RoadmapSession r : roadmaps) {
            int total = r.getTotalNodes() != null ? r.getTotalNodes() : 0;
            long completed = r.getProgressList() != null ?
                    r.getProgressList().stream()
                            .filter(p -> p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                            .count() : 0;
            int progress = total > 0 ? (int) ((completed * 100) / total) : 0;

            if (progress >= 100) {
                completedRoadmaps++;
            } else if (progress > 0) {
                inProgressRoadmaps++;
            }
            totalProgress += progress;

            roadmapDetails.add(StudentLearningReportResponse.RoadmapProgress.builder()
                    .roadmapId(r.getId())
                    .title(r.getTitle())
                    .goal(r.getValidatedGoal() != null ? r.getValidatedGoal() : r.getOriginalGoal())
                    .totalQuests(total)
                    .completedQuests((int) completed)
                    .progressPercent(progress)
                    .createdAt(r.getCreatedAt())
                    .build());
        }

        int averageProgress = totalRoadmaps > 0 ? totalProgress / totalRoadmaps : 0;

        // Study time metrics
        List<StudySession> studySessions = List.of();
        try {
            studySessions = studySessionRepository.findByUserId(studentId);
        } catch (Exception e) {
            log.warn("Could not fetch study sessions for student {}", studentId);
        }

        // Calculate duration from startTime and endTime (convert to VN timezone for comparison)
        int studyTimeToday = studySessions.stream()
                .filter(s -> s.getStartTime() != null && convertToVnTimezone(s.getStartTime()).isAfter(startOfDay))
                .mapToInt(s -> calculateDurationMinutes(s))
                .sum();

        int studyTimeWeek = studySessions.stream()
                .filter(s -> s.getStartTime() != null && convertToVnTimezone(s.getStartTime()).isAfter(startOfWeek))
                .mapToInt(s -> calculateDurationMinutes(s))
                .sum();

        int studyTimeMonth = studySessions.stream()
                .filter(s -> s.getStartTime() != null && convertToVnTimezone(s.getStartTime()).isAfter(startOfMonth))
                .mapToInt(s -> calculateDurationMinutes(s))
                .sum();

        // Streak calculation (simplified)
        int streakDays = calculateStreak(studySessions);

        // Task metrics
        List<Task> tasks = List.of();
        try {
            tasks = taskRepository.findByUserId(studentId);
        } catch (Exception e) {
            log.warn("Could not fetch tasks for student {}", studentId);
        }
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream()
                .filter(t -> "DONE".equalsIgnoreCase(t.getStatus()) || (t.getUserProgress() != null && t.getUserProgress() >= 100))
                .count();

        // Chat sessions count
        int chatSessionsCount = 0;
        try {
            chatSessionsCount = aiChatbotService.getUserSessions(studentId).size();
        } catch (Exception e) {
            log.warn("Could not fetch chat sessions for student {}", studentId);
        }

        // Extract top skills from roadmaps
        List<StudentLearningReportResponse.SkillInfo> topSkills = extractSkillsFromRoadmaps(roadmaps);

        // Course enrollment metrics
        List<CourseEnrollment> enrollments = List.of();
        try {
            enrollments = courseEnrollmentRepository.findActiveEnrollmentsByUserId(studentId);
        } catch (Exception e) {
            log.warn("Could not fetch course enrollments for student {}", studentId);
        }
        int totalEnrolledCourses = enrollments.size();
        int completedCourses = (int) enrollments.stream()
                .filter(e -> e.getProgressPercent() != null && e.getProgressPercent() >= 100)
                .count();

        // Calculate total study hours (sum of all study time in month)
        int totalStudyMinutes = studyTimeMonth;
        int totalStudyHours = totalStudyMinutes / 60;

        return StudentLearningReportResponse.StudentMetrics.builder()
                .totalRoadmaps(totalRoadmaps)
                .completedRoadmaps(completedRoadmaps)
                .inProgressRoadmaps(inProgressRoadmaps)
                .averageProgress(averageProgress)
                .totalStudyMinutesToday(studyTimeToday)
                .totalStudyMinutesWeek(studyTimeWeek)
                .totalStudyMinutesMonth(studyTimeMonth)
                .totalStudyHours(totalStudyHours)
                .streakDays(streakDays)
                .currentStreak(streakDays)  // Frontend expectation
                .totalChatSessions(chatSessionsCount)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .totalTasksCompleted(completedTasks)  // Frontend expectation
                .totalEnrolledCourses(totalEnrolledCourses)
                .completedCourses(completedCourses)
                .topSkills(topSkills)
                .roadmapDetails(roadmapDetails)
                .build();
    }

    private List<RoadmapSessionSummary> getRoadmapSummaries(Long studentId) {
        List<RoadmapSession> sessions = roadmapSessionRepository.findByUserIdOrderByCreatedAtDesc(studentId);

        return sessions.stream().map(s -> {
            int totalQuests = s.getTotalNodes() != null ? s.getTotalNodes() : 0;
            long completedQuests = s.getProgressList() != null ?
                    s.getProgressList().stream()
                            .filter(p -> p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                            .count() : 0;
            int progressPercentage = totalQuests > 0 ? (int) ((completedQuests * 100) / totalQuests) : 0;

            return RoadmapSessionSummary.builder()
                    .sessionId(s.getId())
                    .title(s.getTitle())
                    .roadmapMode(s.getRoadmapMode())
                    .originalGoal(s.getOriginalGoal())
                    .validatedGoal(s.getValidatedGoal())
                    .duration(s.getDuration())
                    .experienceLevel(s.getExperienceLevel())
                    .learningStyle(s.getLearningStyle())
                    .totalQuests(totalQuests)
                    .completedQuests((int) completedQuests)
                    .progressPercentage(progressPercentage)
                    .createdAt(s.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<ChatSessionSummary> getChatSessionSummaries(Long studentId) {
        try {
            return aiChatbotService.getUserSessions(studentId);
        } catch (Exception e) {
            log.warn("Could not fetch chat sessions for student {}", studentId);
            return List.of();
        }
    }

    private String buildDataContext(String studentName, StudentLearningReportResponse.StudentMetrics metrics,
                                    List<RoadmapSessionSummary> roadmaps, List<ChatSessionSummary> chatSessions,
                                    GenerateStudentReportRequest request) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("## Dữ liệu học tập của: ").append(studentName).append("\n\n");

        // Metrics overview
        ctx.append("### Tổng quan:\n");
        ctx.append("- Thời gian học hôm nay: ").append(metrics.getTotalStudyMinutesToday()).append(" phút\n");
        ctx.append("- Thời gian học tuần này: ").append(metrics.getTotalStudyMinutesWeek()).append(" phút\n");
        ctx.append("- Thời gian học tháng này: ").append(metrics.getTotalStudyMinutesMonth()).append(" phút\n");
        ctx.append("- Streak: ").append(metrics.getStreakDays()).append(" ngày liên tục\n");
        ctx.append("- Tổng số roadmap: ").append(metrics.getTotalRoadmaps()).append("\n");
        ctx.append("- Roadmap hoàn thành: ").append(metrics.getCompletedRoadmaps()).append("\n");
        ctx.append("- Roadmap đang học: ").append(metrics.getInProgressRoadmaps()).append("\n");
        ctx.append("- Tiến độ trung bình: ").append(metrics.getAverageProgress()).append("%\n");
        ctx.append("- Số phiên chat AI: ").append(metrics.getTotalChatSessions()).append("\n");
        ctx.append("- Tasks: ").append(metrics.getCompletedTasks()).append("/").append(metrics.getTotalTasks()).append(" hoàn thành\n");
        ctx.append("- Khóa học đã đăng ký: ").append(metrics.getTotalEnrolledCourses()).append("\n");
        ctx.append("- Khóa học hoàn thành: ").append(metrics.getCompletedCourses()).append("\n");

        // Roadmap details
        if (request.getIncludeRoadmapDetails() && !roadmaps.isEmpty()) {
            ctx.append("\n### Roadmaps (").append(roadmaps.size()).append(" lộ trình):\n");
            for (RoadmapSessionSummary r : roadmaps) {
                String goal = r.getValidatedGoal() != null ? r.getValidatedGoal() :
                        (r.getOriginalGoal() != null ? r.getOriginalGoal() : r.getTitle());
                ctx.append("- **").append(goal != null ? goal : "Chưa có mục tiêu").append("**")
                        .append(" | Tiến độ: ").append(r.getProgressPercentage() != null ? r.getProgressPercentage() : 0).append("%")
                        .append(" | Level: ").append(r.getExperienceLevel() != null ? r.getExperienceLevel() : "N/A")
                        .append(" | Tạo ngày: ").append(r.getCreatedAt()).append("\n");
            }
        }

        // Chat session summary
        if (request.getIncludeChatHistory() && !chatSessions.isEmpty()) {
            ctx.append("\n### Phiên Chat AI (").append(chatSessions.size()).append(" phiên gần đây):\n");
            int count = 0;
            for (ChatSessionSummary s : chatSessions) {
                if (count++ >= 10) break; // Limit to 10 most recent
                ctx.append("- Chủ đề: ").append(s.getTitle() != null ? s.getTitle() : "Không có tiêu đề")
                        .append(" | ").append(s.getMessageCount()).append(" tin nhắn")
                        .append(" | Ngày: ").append(s.getLastMessageAt()).append("\n");
            }
        }

        // Skills from roadmaps
        if (metrics.getTopSkills() != null && !metrics.getTopSkills().isEmpty()) {
            ctx.append("\n### Kỹ năng đang phát triển:\n");
            for (StudentLearningReportResponse.SkillInfo skill : metrics.getTopSkills()) {
                ctx.append("- ").append(skill.getSkillName())
                        .append(" (").append(skill.getLevel()).append(")")
                        .append(" | Tiến độ: ").append(skill.getProgressPercent()).append("%\n");
            }
        }

        // Focus skills if provided
        if (request.getFocusSkills() != null && request.getFocusSkills().length > 0) {
            ctx.append("\n### Kỹ năng muốn tập trung đánh giá:\n");
            for (String skill : request.getFocusSkills()) {
                ctx.append("- ").append(skill).append("\n");
            }
        }

        return ctx.toString();
    }

    private String getSystemPrompt(StudentLearningReport.ReportType reportType) {
        String basePrompt = """
            Bạn là chuyên gia phân tích học tập của SkillVerse. Nhiệm vụ: Tạo báo cáo học tập CÁ NHÂN cho học viên
            dựa trên dữ liệu thực tế. Báo cáo phải có giọng văn ĐỘNG VIÊN, TÍCH CỰC nhưng TRUNG THỰC.
            
            Sử dụng ngôi thứ hai "bạn" khi nói với học viên.
            QUAN TRỌNG: Dựa vào dữ liệu thực tế được cung cấp. Nếu thiếu dữ liệu, hãy ghi nhận điều đó thay vì bịa ra.
            
            QUY TẮC ĐỊNH DẠNG BẮT BUỘC:
            - Mỗi phần chính dùng heading cấp 2: ## N. TIÊU ĐỀ
            - Bên trong mỗi phần, BẮT BUỘC chia thành ít nhất 2-3 mục con với heading cấp 3: ### Tên mục con
            - KHÔNG được để dư ký tự ** hoặc __ ở cuối bất kỳ phần nào
            - KHÔNG bọc tiêu đề heading ## trong dấu ** (sai: ## **1. TIÊU ĐỀ**, đúng: ## 1. TIÊU ĐỀ)
            - Mỗi heading ### phải có nội dung bullet points bên dưới
            """;

        return switch (reportType) {
            case COMPREHENSIVE -> basePrompt + """
                
                Báo cáo TOÀN DIỆN PHẢI có CHÍNH XÁC 9 phần sau (mỗi phần có heading ## tương ứng):

                ## 1. KỸ NĂNG HIỆN CÓ
                Phân thành các mục con ### cho từng nhóm kỹ năng:
                ### Kỹ năng đang học
                ### Phân loại mức độ
                ### Nguồn kỹ năng

                ## 2. MỤC TIÊU HỌC TẬP
                ### Danh sách mục tiêu
                ### Đánh giá mức độ rõ ràng
                ### Đề xuất điều chỉnh

                ## 3. TIẾN ĐỘ HỌC TẬP
                ### Tiến độ roadmap
                ### Thời gian học tập
                ### So sánh với mục tiêu

                ## 4. ĐIỂM MẠNH CỦA BẠN
                ### Kỹ năng nổi bật
                ### Thói quen tích cực

                ## 5. LĨNH VỰC CẦN CẢI THIỆN
                ### Điểm chưa đạt
                ### Thói quen cần điều chỉnh

                ## 6. KHOẢNG TRỐNG KỸ NĂNG
                ### Kỹ năng còn thiếu
                ### Lộ trình bổ sung

                ## 7. KHUYẾN NGHỊ CÁ NHÂN
                ### Phương pháp học
                ### Tài nguyên gợi ý

                ## 8. CÁC BƯỚC TIẾP THEO
                ### Action items
                ### Timeline đề xuất

                ## 9. ĐỘNG LỰC & KHÍCH LỆ
                ### Ghi nhận thành tích
                ### Lời động viên

                Mỗi phần PHẢI có nội dung cụ thể. Sử dụng bullet points và emoji phù hợp.
                KHÔNG để dư ký tự ** ở cuối phần. KHÔNG bọc heading trong **.
                """;

            case WEEKLY_SUMMARY -> basePrompt + """
                
                Báo cáo TÓM TẮT TUẦN ngắn gọn với 5 phần:
                ## 1. TUẦN NÀY BẠN ĐÃ LÀM GÌ
                ## 2. THÀNH TỰU NỔI BẬT
                ## 3. ĐIỀU CẦN CẢI THIỆN
                ## 4. MỤC TIÊU TUẦN TỚI
                ## 5. LỜI ĐỘNG VIÊN
                
                Giữ mỗi phần ngắn gọn (3-5 bullet points).
                """;

            case MONTHLY_SUMMARY -> basePrompt + """
                
                Báo cáo TÓM TẮT THÁNG với 6 phần:
                ## 1. TỔNG KẾT THÁNG
                ## 2. TIẾN BỘ QUAN TRỌNG
                ## 3. KỸ NĂNG ĐÃ PHÁT TRIỂN
                ## 4. THÁCH THỨC ĐÃ VƯỢT QUA
                ## 5. KẾ HOẠCH THÁNG TỚI
                ## 6. THÔNG ĐIỆP THÁNG MỚI
                """;

            case SKILL_ASSESSMENT -> basePrompt + """
                
                Báo cáo ĐÁNH GIÁ KỸ NĂNG chuyên sâu:
                ## 1. TỔNG QUAN KỸ NĂNG
                ## 2. PHÂN TÍCH CHI TIẾT TỪNG KỸ NĂNG
                ## 3. KỸ NĂNG MẠNH NHẤT
                ## 4. KỸ NĂNG CẦN PHÁT TRIỂN
                ## 5. LỘ TRÌNH NÂNG CAO
                ## 6. TÀI NGUYÊN ĐỀ XUẤT
                """;

            case GOAL_TRACKING -> basePrompt + """
                
                Báo cáo THEO DÕI MỤC TIÊU:
                ## 1. MỤC TIÊU ĐANG THEO ĐUỔI
                ## 2. TIẾN ĐỘ TỪNG MỤC TIÊU
                ## 3. MỤC TIÊU SẮP HOÀN THÀNH
                ## 4. MỤC TIÊU CẦN ĐẨY NHANH
                ## 5. ĐIỀU CHỈNH ĐỀ XUẤT
                ## 6. MILESTONE TIẾP THEO
                """;
        };
    }

    private StudentLearningReportResponse.ReportSections parseSections(String content) {
        return StudentLearningReportResponse.ReportSections.builder()
                .currentSkills(extractSectionByHeading(content, 1, 2))
                .learningGoals(extractSectionByHeading(content, 2, 3))
                .progressSummary(extractSectionByHeading(content, 3, 4))
                .strengths(extractSectionByHeading(content, 4, 5))
                .areasToImprove(extractSectionByHeading(content, 5, 6))
                .skillGaps(extractSectionByHeading(content, 6, 7))
                .recommendations(extractSectionByHeading(content, 7, 8))
                .nextSteps(extractSectionByHeading(content, 8, 9))
                .motivation(extractSectionByHeading(content, 9, -1))
                .build();
    }

    /**
     * Regex pattern that matches a level-2 heading line produced by the AI model.
     * Handles variations such as:
     *   ## 1. KỸ NĂNG HIỆN CÓ
     *   ## **1. KỸ NĂNG HIỆN CÓ**
     *   **## 1. KỸ NĂNG HIỆN CÓ**
     *   1. KỸ NĂNG HIỆN CÓ (without ##)
     */
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:\\*{2})?\\s*(?:##\\s*)?(?:\\*{2})?\\s*(\\d+)\\.\\s",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract a section of AI-generated content between heading N and heading nextN.
     * Uses regex to robustly find "## N." heading patterns regardless of bold markers.
     *
     * @param content   Full AI report content
     * @param sectionNum   Section number to extract (e.g. 1)
     * @param nextSectionNum  Next section number (-1 means extract until end)
     */
    private String extractSectionByHeading(String content, int sectionNum, int nextSectionNum) {
        try {
            if (content == null || content.isBlank()) return "Không có dữ liệu";

            // Find the heading for sectionNum
            Matcher matcher = SECTION_HEADING_PATTERN.matcher(content);
            int startIdx = -1;
            while (matcher.find()) {
                int num = Integer.parseInt(matcher.group(1));
                if (num == sectionNum) {
                    // Move past the entire heading line
                    int lineEnd = content.indexOf('\n', matcher.end());
                    startIdx = (lineEnd == -1) ? matcher.end() : lineEnd + 1;
                    break;
                }
            }
            if (startIdx == -1) return "Không có dữ liệu";

            // Find the heading for nextSectionNum
            int endIdx = content.length();
            if (nextSectionNum > 0) {
                matcher = SECTION_HEADING_PATTERN.matcher(content);
                while (matcher.find()) {
                    if (matcher.start() <= startIdx) continue;
                    int num = Integer.parseInt(matcher.group(1));
                    if (num == nextSectionNum) {
                        endIdx = matcher.start();
                        break;
                    }
                }
            }

            String section = content.substring(startIdx, endIdx).trim();
            section = stripTrailingEmphasisMarkers(section);
            return section.isEmpty() ? "Không có dữ liệu" : section;
        } catch (Exception e) {
            log.warn("Failed to extract section {} from report content", sectionNum, e);
            return "Không có dữ liệu";
        }
    }

    /**
     * Remove trailing orphan emphasis markers (**, __, ***, etc.) that the AI model
     * sometimes appends at the end of a section.
     */
    private String stripTrailingEmphasisMarkers(String text) {
        if (text == null) return "";
        // Strip trailing lines that only contain emphasis markers
        text = text.replaceAll("(?m)^\\s*[*_]{2,}\\s*$", "").trim();
        // Strip trailing emphasis markers at very end of content
        text = text.replaceAll("\\s*[*_]{2,}\\s*$", "").trim();
        return text;
    }

    private StudentLearningReport saveReport(User student, String studentName, String reportContent,
                                              StudentLearningReportResponse.ReportSections sections,
                                              boolean isAiGenerated, StudentLearningReport.ReportType reportType) {
        StudentLearningReport report = StudentLearningReport.builder()
                .student(student)
                .studentName(studentName)
                .reportContent(reportContent)
                .currentSkillsSection(sections.getCurrentSkills())
                .learningGoalsSection(sections.getLearningGoals())
                .progressSection(sections.getProgressSummary())
                .strengthsSection(sections.getStrengths())
                .areasToImproveSection(sections.getAreasToImprove())
                .recommendationsSection(sections.getRecommendations())
                .skillGapsSection(sections.getSkillGaps())
                .nextStepsSection(sections.getNextSteps())
                .motivationSection(sections.getMotivation())
                .isAiGenerated(isAiGenerated)
                .reportType(reportType)
                .build();

        return reportRepository.save(report);
    }

    private StudentLearningReportResponse generateFallbackReport(Long studentId, String studentName,
                                                                   StudentLearningReportResponse.StudentMetrics metrics,
                                                                   List<RoadmapSessionSummary> roadmaps,
                                                                   StudentLearningReport.ReportType reportType) {
        StringBuilder content = new StringBuilder();
        content.append("# 📊 BÁO CÁO HỌC TẬP CÁ NHÂN\n\n");
        content.append("**Học viên:** ").append(studentName).append("\n\n");

        // Section 1 - Skills
        content.append("## 1. KỸ NĂNG HIỆN CÓ\n");
        if (roadmaps.isEmpty()) {
            content.append("- Bạn chưa bắt đầu roadmap nào. Hãy tạo roadmap đầu tiên để xác định kỹ năng cần học!\n");
        } else {
            content.append("Các kỹ năng đang phát triển:\n");
            for (RoadmapSessionSummary r : roadmaps) {
                String goal = r.getValidatedGoal() != null ? r.getValidatedGoal() :
                        (r.getOriginalGoal() != null ? r.getOriginalGoal() : r.getTitle());
                content.append("- **").append(goal != null ? goal : "Kỹ năng mới").append("**")
                        .append(" (").append(r.getProgressPercentage()).append("% hoàn thành)\n");
            }
        }

        // Section 2 - Goals
        content.append("\n## 2. MỤC TIÊU HỌC TẬP\n");
        if (roadmaps.isEmpty()) {
            content.append("- Chưa có mục tiêu cụ thể. Hãy tạo roadmap để đặt mục tiêu học tập!\n");
        } else {
            for (RoadmapSessionSummary r : roadmaps) {
                String goal = r.getValidatedGoal() != null ? r.getValidatedGoal() : r.getOriginalGoal();
                if (goal != null) {
                    content.append("- ").append(goal).append("\n");
                }
            }
        }

        // Section 3 - Progress
        content.append("\n## 3. TIẾN ĐỘ HỌC TẬP\n");
        content.append("- Thời gian học hôm nay: ").append(metrics.getTotalStudyMinutesToday()).append(" phút\n");
        content.append("- Thời gian học tuần này: ").append(metrics.getTotalStudyMinutesWeek()).append(" phút\n");
        content.append("- Tiến độ trung bình: ").append(metrics.getAverageProgress()).append("%\n");
        content.append("- Streak: ").append(metrics.getStreakDays()).append(" ngày liên tục\n");

        // Section 4 - Strengths
        content.append("\n## 4. ĐIỂM MẠNH CỦA BẠN\n");
        if (metrics.getStreakDays() > 3) {
            content.append("- ✅ Duy trì streak tốt (").append(metrics.getStreakDays()).append(" ngày)\n");
        }
        if (!roadmaps.isEmpty()) {
            content.append("- ✅ Chủ động tạo roadmap học tập\n");
        }
        if (metrics.getTotalChatSessions() > 0) {
            content.append("- ✅ Tích cực sử dụng AI mentor\n");
        }
        if (metrics.getCompletedRoadmaps() > 0) {
            content.append("- ✅ Đã hoàn thành ").append(metrics.getCompletedRoadmaps()).append(" roadmap\n");
        }

        // Section 5 - Areas to improve
        content.append("\n## 5. LĨNH VỰC CẦN CẢI THIỆN\n");
        if (metrics.getStreakDays() == 0) {
            content.append("- ⚠️ Cần học đều đặn hơn để xây dựng streak\n");
        }
        if (metrics.getAverageProgress() < 30) {
            content.append("- ⚠️ Tiến độ học tập cần được đẩy nhanh\n");
        }
        if (roadmaps.isEmpty()) {
            content.append("- ⚠️ Cần tạo roadmap để có định hướng rõ ràng\n");
        }

        // Section 6 - Skill gaps
        content.append("\n## 6. KHOẢNG TRỐNG KỸ NĂNG\n");
        content.append("- Cần phân tích thêm dữ liệu để xác định khoảng trống\n");

        // Section 7 - Recommendations
        content.append("\n## 7. KHUYẾN NGHỊ CÁ NHÂN\n");
        content.append("- 💡 Học đều đặn mỗi ngày, dù chỉ 15-30 phút\n");
        content.append("- 💡 Sử dụng AI mentor khi gặp khó khăn\n");
        content.append("- 💡 Hoàn thành từng quest nhỏ trong roadmap\n");

        // Section 8 - Next steps
        content.append("\n## 8. CÁC BƯỚC TIẾP THEO\n");
        content.append("- 📌 Xem lại roadmap hiện tại và tiếp tục quest tiếp theo\n");
        content.append("- 📌 Đặt mục tiêu học 30 phút mỗi ngày\n");
        content.append("- 📌 Hoàn thành ít nhất 1 quest trong tuần\n");

        // Section 9 - Motivation
        content.append("\n## 9. ĐỘNG LỰC & KHÍCH LỆ\n");
        content.append("- 🌟 Mỗi bước nhỏ đều đưa bạn đến gần mục tiêu hơn!\n");
        content.append("- 🌟 \"The expert in anything was once a beginner.\" - Helen Hayes\n");
        content.append("- 🌟 Hãy tiếp tục cố gắng, SkillVerse tin vào bạn! 💪\n");

        String reportContent = content.toString();
        StudentLearningReportResponse.ReportSections sections = parseSections(reportContent);

        return StudentLearningReportResponse.builder()
                .generatedAt(LocalDateTime.now(VN_ZONE))
                .studentId(studentId)
                .studentName(studentName)
                .reportContent(reportContent)
                .sections(sections)
                .metrics(metrics)
                .reportType(reportType.name())
                .build();
    }

    private StudentLearningReportResponse convertToResponse(StudentLearningReport report) {
        String resolvedName = resolveStudentName(report.getStudent());

        return StudentLearningReportResponse.builder()
                .id(report.getId())
                .generatedAt(report.getGeneratedAt())
                .studentId(report.getStudent().getId())
                .studentName(resolvedName)
                .reportContent(report.getReportContent())
                .sections(StudentLearningReportResponse.ReportSections.builder()
                        .currentSkills(report.getCurrentSkillsSection())
                        .learningGoals(report.getLearningGoalsSection())
                        .progressSummary(report.getProgressSection())
                        .strengths(report.getStrengthsSection())
                        .areasToImprove(report.getAreasToImproveSection())
                        .recommendations(report.getRecommendationsSection())
                        .skillGaps(report.getSkillGapsSection())
                        .nextSteps(report.getNextStepsSection())
                        .motivation(report.getMotivationSection())
                        .build())
                .reportType(report.getReportType().name())
                .build();
    }

    private StudentLearningReportResponse buildResponse(StudentLearningReport report,
                                                         StudentLearningReportResponse.StudentMetrics metrics) {
        StudentLearningReportResponse response = convertToResponse(report);
        response.setMetrics(metrics);
        return response;
    }

    private String resolveStudentName(User student) {
        if (student == null) return "Học viên";

        if (student.getFullName() != null && !student.getFullName().trim().isEmpty()) {
            return student.getFullName().trim();
        }

        String combined = ((student.getFirstName() != null ? student.getFirstName() : "") + " " +
                (student.getLastName() != null ? student.getLastName() : "")).trim();
        if (!combined.isEmpty()) {
            return combined;
        }

        if (student.getEmail() != null && student.getEmail().contains("@")) {
            return student.getEmail().split("@")[0];
        }

        return "Học viên";
    }

    private int calculateStreak(List<StudySession> sessions) {
        if (sessions.isEmpty()) return 0;

        LocalDateTime today = LocalDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.DAYS);
        int streak = 0;

        for (int i = 0; i < 365; i++) {
            LocalDateTime checkDate = today.minusDays(i);
            LocalDateTime nextDate = checkDate.plusDays(1);

            boolean hasStudy = sessions.stream()
                    .anyMatch(s -> s.getStartTime() != null &&
                            convertToVnTimezone(s.getStartTime()).isAfter(checkDate) &&
                            convertToVnTimezone(s.getStartTime()).isBefore(nextDate));

            if (hasStudy) {
                streak++;
            } else if (i > 0) {
                break;
            }
        }

        return streak;
    }

    private List<StudentLearningReportResponse.SkillInfo> extractSkillsFromRoadmaps(List<RoadmapSession> roadmaps) {
        List<StudentLearningReportResponse.SkillInfo> skills = new ArrayList<>();

        for (RoadmapSession r : roadmaps) {
            String goal = r.getValidatedGoal() != null ? r.getValidatedGoal() :
                    (r.getOriginalGoal() != null ? r.getOriginalGoal() : r.getTitle());

            if (goal != null) {
                int total = r.getTotalNodes() != null ? r.getTotalNodes() : 0;
                long completed = r.getProgressList() != null ?
                        r.getProgressList().stream()
                                .filter(p -> p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                                .count() : 0;
                int progress = total > 0 ? (int) ((completed * 100) / total) : 0;

                String level;
                if (progress >= 80) level = "Advanced";
                else if (progress >= 50) level = "Intermediate";
                else if (progress >= 20) level = "Beginner+";
                else level = "Beginner";

                skills.add(StudentLearningReportResponse.SkillInfo.builder()
                        .skillName(goal)
                        .level(level)
                        .progressPercent(progress)
                        .source(r.getTitle())
                        .build());
            }
        }

        return skills;
    }

    /**
     * Tính thời gian học (phút) từ startTime và endTime của StudySession.
     */
    private int calculateDurationMinutes(StudySession session) {
        if (session.getStartTime() == null || session.getEndTime() == null) {
            return 0;
        }
        // Convert both times to VN timezone before calculating duration
        LocalDateTime startVn = convertToVnTimezone(session.getStartTime());
        LocalDateTime endVn = convertToVnTimezone(session.getEndTime());
        long minutes = ChronoUnit.MINUTES.between(startVn, endVn);
        return minutes > 0 ? (int) minutes : 0;
    }

    /**
     * Convert LocalDateTime to Vietnam timezone (Asia/Ho_Chi_Minh).
     * Assumes the stored time is in UTC and adds 7 hours.
     */
    private LocalDateTime convertToVnTimezone(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        // Assume stored time is UTC, add 7 hours for Vietnam timezone
        return dateTime.plusHours(7);
    }
}
