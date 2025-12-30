package com.exe.skillverse_backend.parent_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.parent_service.dto.request.LinkStudentRequest;
import com.exe.skillverse_backend.parent_service.dto.request.UpdateLinkStatusRequest;
import com.exe.skillverse_backend.parent_service.dto.response.ParentDashboardResponse;
import com.exe.skillverse_backend.parent_service.dto.response.ParentStudentLinkResponse;
import com.exe.skillverse_backend.parent_service.dto.response.StudentOverviewDTO;
import com.exe.skillverse_backend.parent_service.dto.response.LearningReportResponse;
import com.exe.skillverse_backend.parent_service.entity.ParentStudentLink;
import com.exe.skillverse_backend.parent_service.entity.LearningReport;
import com.exe.skillverse_backend.parent_service.entity.enums.LinkStatus;
import com.exe.skillverse_backend.parent_service.repository.ParentStudentLinkRepository;
import com.exe.skillverse_backend.parent_service.repository.LearningReportRepository;
import com.exe.skillverse_backend.parent_service.service.ParentService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioProjectRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.ai_service.service.AiChatbotService;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ParentServiceImpl implements ParentService {

    private final ParentStudentLinkRepository linkRepository;
    private final LearningReportRepository learningReportRepository;
    private final UserRepository userRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserProfileRepository userProfileRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final StudySessionRepository studySessionRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;
    private final TaskRepository taskRepository;
    private final AiChatbotService aiChatbotService;
    private final ChatModel mistralChatModel;

    public ParentServiceImpl(
            ParentStudentLinkRepository linkRepository,
            LearningReportRepository learningReportRepository,
            UserRepository userRepository,
            CourseEnrollmentRepository enrollmentRepository,
            UserMapper userMapper,
            NotificationService notificationService,
            EmailService emailService,
            UserProfileRepository userProfileRepository,
            UserSubscriptionRepository subscriptionRepository,
            StudySessionRepository studySessionRepository,
            RoadmapSessionRepository roadmapSessionRepository,
            PortfolioProjectRepository portfolioProjectRepository,
            TaskRepository taskRepository,
            AiChatbotService aiChatbotService,
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel) {
        this.linkRepository = linkRepository;
        this.learningReportRepository = learningReportRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.userProfileRepository = userProfileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.studySessionRepository = studySessionRepository;
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.portfolioProjectRepository = portfolioProjectRepository;
        this.taskRepository = taskRepository;
        this.aiChatbotService = aiChatbotService;
        this.mistralChatModel = mistralChatModel;
    }

    @Override
    @Transactional
    public ParentStudentLinkResponse sendLinkRequest(Long parentId, LinkStudentRequest request) {
        User parent = userRepository.findById(parentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Parent not found"));
        
        User student = userRepository.findByEmail(request.getStudentEmail())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Student not found with email: " + request.getStudentEmail()));

        if (linkRepository.existsByParentIdAndStudentId(parentId, student.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Link already exists");
        }

        // Limit check: Student max 2 parents
        List<ParentStudentLink> studentLinks = linkRepository.findByStudentId(student.getId());
        long activeParents = studentLinks.stream()
                .filter(l -> l.getStatus() == LinkStatus.ACTIVE)
                .count();
        if (activeParents >= 2) {
             throw new ApiException(ErrorCode.CONFLICT, "Student already has maximum number of parents linked");
        }

        ParentStudentLink link = ParentStudentLink.builder()
                .parent(parent)
                .student(student)
                .status(LinkStatus.PENDING)
                .inviteCode(request.getInviteCode() != null ? request.getInviteCode() : UUID.randomUUID().toString().substring(0, 8))
                .build();

        link = linkRepository.save(link);

        // Send Notification
        notificationService.createNotification(
                student.getId(),
                "Yêu cầu kết nối Phụ huynh",
                parent.getFullName() + " muốn kết nối với bạn.",
                NotificationType.SYSTEM,
                link.getId().toString(),
                parent.getId()
        );

        // Send Email
        String emailContent = String.format(
                "<h3>Yêu cầu kết nối từ Phụ huynh</h3>" +
                "<p>Xin chào %s,</p>" +
                "<p>Phụ huynh <b>%s</b> (%s) muốn kết nối với tài khoản SkillVerse của bạn.</p>" +
                "<p>Vui lòng đăng nhập vào hệ thống và kiểm tra mục <b>Yêu cầu kết nối</b> để chấp nhận hoặc từ chối.</p>" +
                "<p>Trân trọng,<br/>Đội ngũ SkillVerse</p>",
                student.getFullName(), parent.getFullName(), parent.getEmail()
        );
        emailService.sendHtmlEmail(student.getEmail(), "SkillVerse - Yêu cầu kết nối Phụ huynh", emailContent);

        return mapToResponse(link);
    }

    @Override
    @Transactional
    public ParentStudentLinkResponse updateLinkStatus(Long userId, Long linkId, UpdateLinkStatusRequest request) {
        ParentStudentLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Link not found"));

        // Only student can accept/reject if invited by parent
        // Logic simplified for now: User must be either student or parent
        
        if (!link.getStudent().getId().equals(userId) && !link.getParent().getId().equals(userId)) {
             throw new ApiException(ErrorCode.FORBIDDEN, "Unauthorized to update this link");
        }

        link.setStatus(request.getStatus());
        link = linkRepository.save(link);
        return mapToResponse(link);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public ParentDashboardResponse getParentDashboard(Long parentId) {
        List<ParentStudentLink> links = linkRepository.findByParentIdAndStatus(parentId, LinkStatus.ACTIVE);
        
        List<StudentOverviewDTO> students = links.stream()
                .map(link -> {
                    try {
                        return getStudentOverview(link.getStudent());
                    } catch (Exception e) {
                        log.error("Failed to build student overview for parent {} and student {}", parentId, link.getStudent().getId(), e);
                        return null; // Skip this student but continue others
                    }
                })
                .filter(overview -> overview != null)
                .collect(Collectors.toList());

        return ParentDashboardResponse.builder()
                .students(students)
                .totalStudents(students.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParentStudentLinkResponse> getStudentLinks(Long studentId) {
        return linkRepository.findByStudentId(studentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParentStudentLinkResponse> getSentLinkRequests(Long parentId) {
        return linkRepository.findByParentIdAndStatus(parentId, LinkStatus.PENDING).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void unlink(Long userId, Long linkId) {
        ParentStudentLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Link not found"));
        
        if (!link.getParent().getId().equals(userId) && !link.getStudent().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Unauthorized");
        }
        
        linkRepository.delete(link);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapSessionSummary> getStudentRoadmaps(Long parentId, Long studentId) {
        // Verify link
        if (!linkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
             throw new ApiException(ErrorCode.FORBIDDEN, "Not linked to this student");
        }
        
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
        })
        .collect(Collectors.toList());
    }

    private StudentOverviewDTO getStudentOverview(User student) {
        List<CourseEnrollment> enrollments = List.of();
        try {
            enrollments = enrollmentRepository.findByUserId(student.getId(), Pageable.unpaged()).getContent();
        } catch (Exception e) {
            log.error("Failed to load enrollments for student {}", student.getId(), e);
        }
        
        long completed = enrollments.stream().filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED).count();
        long inProgress = enrollments.stream().filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED).count();
        
        double avgProgress = enrollments.isEmpty() ? 0 : enrollments.stream()
                .mapToInt(e -> e.getProgressPercent() != null ? e.getProgressPercent() : 0)
                .average().orElse(0);

        String status = "Good";
        if (avgProgress < 30 && inProgress > 0) status = "Behind";
        if (avgProgress < 10 && inProgress > 0) status = "Risk";

        UserDto studentDto = toUserDtoWithAvatar(student);

        // Fetch Premium Plan
        String premiumPlan = "Free";
        String premiumExpiry = null;
        
        try {
             Optional<UserSubscription> activeSub = subscriptionRepository.findByUserAndIsActiveTrue(student);
             if (activeSub.isPresent()) {
                 premiumPlan = activeSub.get().getPlan().getDisplayName();
                 premiumExpiry = activeSub.get().getEndDate().toString();
             }
        } catch (Exception e) {
            log.error("Error fetching subscription for student {}", student.getId(), e);
        }
        
        // Study Time Calculation
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

        List<StudySession> sessions = List.of();
        try {
            sessions = studySessionRepository.findByUserId(student.getId());
        } catch (Exception e) {
            log.error("Failed to load study sessions for student {}", student.getId(), e);
        }
        long todayMins = sessions.stream()
            .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(startOfDay))
            .mapToLong(s -> {
                if (s.getStartTime() != null && s.getEndTime() != null) {
                    return ChronoUnit.MINUTES.between(s.getStartTime(), s.getEndTime());
                }
                return 0;
            }).sum();
        long weekMins = sessions.stream()
            .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(startOfWeek))
            .mapToLong(s -> {
                if (s.getStartTime() != null && s.getEndTime() != null) {
                    return ChronoUnit.MINUTES.between(s.getStartTime(), s.getEndTime());
                }
                return 0;
            }).sum();
        long monthMins = sessions.stream()
            .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(startOfMonth))
            .mapToLong(s -> {
                if (s.getStartTime() != null && s.getEndTime() != null) {
                    return ChronoUnit.MINUTES.between(s.getStartTime(), s.getEndTime());
                }
                return 0;
            }).sum();

        // Roadmap & Chat - count actual chat sessions from AI chatbot
        int totalRoadmaps = 0;
        try {
            totalRoadmaps = roadmapSessionRepository.findByUserIdOrderByCreatedAtDesc(student.getId()).size();
        } catch (Exception e) {
            log.error("Failed to load roadmaps for student {}", student.getId(), e);
        }
        int chatSessionsCount = 0;
        try {
            List<ChatSessionSummary> chatSessions = aiChatbotService.getUserSessions(student.getId());
            chatSessionsCount = chatSessions != null ? chatSessions.size() : 0;
        } catch (Exception e) {
            log.warn("Could not fetch chat sessions count for student {}: {}", student.getId(), e.getMessage());
        }

        // Jobs/Tasks
        long completedJobs = 0;
        try {
            completedJobs = taskRepository.findByUserId(student.getId()).stream()
                .filter(t -> "DONE".equalsIgnoreCase(t.getStatus()) || "COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();
        } catch (Exception e) {
            log.error("Failed to load tasks for student {}", student.getId(), e);
        }

        // Portfolio
        boolean portfolioCreated = false;
        try {
            portfolioCreated = portfolioProjectRepository.countByUserId(student.getId()) > 0;
        } catch (Exception e) {
            log.error("Failed to load portfolio info for student {}", student.getId(), e);
        }

        return StudentOverviewDTO.builder()
                .studentInfo(studentDto)
                .completedCourses((int) completed)
                .inProgressCourses((int) inProgress)
                .overallProgress(avgProgress)
                .streakDays(5) // Mock for now
                .learningStatus(status)
                .premiumPlan(premiumPlan)
                .premiumExpiry(premiumExpiry)
                .studyTimeToday(todayMins)
                .studyTimeWeek(weekMins)
                .studyTimeMonth(monthMins)
                .totalRoadmaps(totalRoadmaps)
                .chatSessionsCount(chatSessionsCount)
                .completedJobs((int) completedJobs)
                .portfolioCreated(portfolioCreated)
                .build();
    }

    private ParentStudentLinkResponse mapToResponse(ParentStudentLink link) {
        return ParentStudentLinkResponse.builder()
                .id(link.getId())
                .parent(toUserDtoWithAvatar(link.getParent()))
                .student(toUserDtoWithAvatar(link.getStudent()))
                .status(link.getStatus())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }

    @Override
    public List<ChatSessionSummary> getStudentChatSessions(Long parentId, Long studentId) {
        // Verify link
        if (!linkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
             throw new ApiException(ErrorCode.FORBIDDEN, "Not linked to this student");
        }
        return aiChatbotService.getUserSessions(studentId);
    }

    @Override
    public List<ChatMessageResponse> getStudentChatSessionDetails(Long parentId, Long studentId, Long sessionId) {
        // Verify link
        if (!linkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
             throw new ApiException(ErrorCode.FORBIDDEN, "Not linked to this student");
        }
        // Verify session belongs to student (optional but good practice, though getUserSessions filters by userId, getConversationHistory might not check ownership if we pass sessionId directly, but aiChatbotService.getConversationHistory takes userId too)
        return aiChatbotService.getConversationHistory(sessionId, studentId);
    }

    private UserDto toUserDtoWithAvatar(User user) {
        UserDto dto = userMapper.toDto(user);
        
        // Fallback for missing names
        if (dto.getFirstName() == null || dto.getFirstName().isEmpty()) {
            String emailName = user.getEmail().split("@")[0];
            dto.setFirstName(emailName);
            dto.setLastName("");
            dto.setFullName(emailName);
        }
        
        userProfileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            if (profile.getAvatarMedia() != null) {
                dto.setAvatarUrl(profile.getAvatarMedia().getUrl());
            }
            // Also try to get name from profile if user entity is empty
            if ((dto.getFirstName() == null || dto.getFirstName().isEmpty()) && profile.getFullName() != null) {
                 dto.setFullName(profile.getFullName());
                 // Simple split attempt
                 String[] parts = profile.getFullName().split(" ", 2);
                 dto.setFirstName(parts[0]);
                 if (parts.length > 1) dto.setLastName(parts[1]);
            }
        });
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public LearningReportResponse generateLearningReport(Long parentId, Long studentId) {
        // Verify link
        if (!linkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not linked to this student");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Student not found"));
        String studentName = student.getFullName() != null ? student.getFullName() : student.getEmail().split("@")[0];

        // Gather student data
        List<RoadmapSessionSummary> roadmaps = getStudentRoadmaps(parentId, studentId);
        List<ChatSessionSummary> chatSessions = getStudentChatSessions(parentId, studentId);
        StudentOverviewDTO overview = getStudentOverview(student);

        // Build context for AI
        StringBuilder dataContext = new StringBuilder();
        dataContext.append("## Dữ liệu học viên: ").append(studentName).append("\n\n");
        
        // Roadmap data
        dataContext.append("### Roadmaps (").append(roadmaps.size()).append(" lộ trình):\n");
        for (RoadmapSessionSummary r : roadmaps) {
            String goal = r.getValidatedGoal() != null ? r.getValidatedGoal() : 
                         (r.getOriginalGoal() != null ? r.getOriginalGoal() : r.getTitle());
            dataContext.append("- ").append(goal != null ? goal : "Chưa có mục tiêu")
                    .append(" | Tiến độ: ").append(r.getProgressPercentage() != null ? r.getProgressPercentage() : 0).append("%")
                    .append(" | Tạo ngày: ").append(r.getCreatedAt()).append("\n");
        }
        
        // Chat session summary
        dataContext.append("\n### Phiên Chat AI (").append(chatSessions.size()).append(" phiên):\n");
        for (ChatSessionSummary s : chatSessions) {
            dataContext.append("- Chủ đề: ").append(s.getTitle() != null ? s.getTitle() : "Không có tiêu đề")
                    .append(" | ").append(s.getMessageCount()).append(" tin nhắn")
                    .append(" | Ngày: ").append(s.getLastMessageAt()).append("\n");
        }
        
        // Progress data - StudentOverviewDTO has flat structure, not nested ProgressData
        dataContext.append("\n### Tổng quan học tập:\n");
        dataContext.append("- Thời gian học hôm nay: ").append(overview.getStudyTimeToday()).append(" phút\n");
        dataContext.append("- Thời gian học tuần này: ").append(overview.getStudyTimeWeek()).append(" phút\n");
        dataContext.append("- Tổng số roadmap: ").append(overview.getTotalRoadmaps()).append("\n");
        dataContext.append("- Số phiên chat AI: ").append(overview.getChatSessionsCount()).append("\n");
        dataContext.append("- Streak: ").append(overview.getStreakDays()).append(" ngày\n");
        dataContext.append("- Trạng thái học: ").append(overview.getLearningStatus()).append("\n");
        if (overview.getPremiumPlan() != null) {
            dataContext.append("- Gói Premium: ").append(overview.getPremiumPlan()).append("\n");
        }

        // AI Prompt
        String systemPrompt = """
            Bạn là chuyên gia phân tích học tập của SkillVerse. Nhiệm vụ: Tạo báo cáo học tập TOÀN DIỆN cho phụ huynh
            dựa trên dữ liệu thực tế của học viên. Báo cáo phải có giọng văn CHUYÊN NGHIỆP, THÂN THIỆN và DỄ HIỂU.

            QUAN TRỌNG: Dựa vào dữ liệu thực tế được cung cấp. Nếu thiếu dữ liệu, hãy ghi nhận điều đó thay vì bịa ra.
            
            Báo cáo PHẢI có CHÍNH XÁC 6 phần sau (mỗi phần có heading ## tương ứng):

            ## 1. MỤC TIÊU HỌC TẬP
            - Liệt kê các roadmap/mục tiêu đang theo đuổi
            - Đánh giá mức độ rõ ràng của mục tiêu
            - Đề xuất cải thiện nếu cần

            ## 2. KẾT QUẢ ĐẠT ĐƯỢC
            - Tổng hợp tiến độ các roadmap (% hoàn thành)
            - Các cột mốc đã đạt
            - So sánh với mục tiêu đề ra

            ## 3. HÀNH VI HỌC TẬP
            - Phân tích thời gian học (ngày/tuần)
            - Tần suất sử dụng hệ thống
            - Mức độ tương tác với AI mentor
            - Streak và tính nhất quán

            ## 4. ĐIỂM MẠNH
            - Những kỹ năng/lĩnh vực xuất sắc
            - Thói quen học tập tích cực
            - Tiềm năng phát triển

            ## 5. ĐIỂM CẦN LƯU Ý
            - Các rủi ro trong quá trình học
            - Khoảng trống kiến thức cần bổ sung
            - Dấu hiệu cần hỗ trợ (nếu có)

            ## 6. KHUYẾN NGHỊ CHO PHỤ HUYNH
            - Cách hỗ trợ con học tập hiệu quả
            - Các hoạt động bổ trợ đề xuất
            - Lời khuyên cụ thể dựa trên dữ liệu

            Mỗi phần PHẢI có nội dung cụ thể, không được để trống. Sử dụng bullet points và emoji phù hợp.
            """;

        String userPrompt = "Dựa trên dữ liệu sau, hãy tạo báo cáo học tập chi tiết:\n\n" + dataContext.toString();

        User parent = userRepository.findById(parentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Parent not found"));

        try {
            ChatClient chatClient = ChatClient.create(mistralChatModel);
            String reportContent = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            // Parse sections from reportContent
            LearningReportResponse.ReportSections sections = parseSections(reportContent);

            // Save to database
            LearningReport savedReport = saveLearningReport(parent, student, studentName, reportContent, sections, true);
            log.info("✅ Learning report saved with ID: {}", savedReport.getId());

            return LearningReportResponse.builder()
                    .id(savedReport.getId())
                    .generatedAt(savedReport.getGeneratedAt())
                    .studentId(studentId)
                    .studentName(studentName)
                    .reportContent(reportContent)
                    .sections(sections)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate learning report with AI", e);
            // Return a fallback report
            return generateFallbackReport(studentId, studentName, overview, roadmaps);
        }
    }

    private LearningReport saveLearningReport(User parent, User student, String studentName,
            String reportContent, LearningReportResponse.ReportSections sections, boolean isAiGenerated) {
        LearningReport report = LearningReport.builder()
                .parent(parent)
                .student(student)
                .studentName(studentName)
                .reportContent(reportContent)
                .goalsSection(sections.getLearningGoals())
                .resultsSection(sections.getAchievements())
                .behaviorSection(sections.getLearningBehavior())
                .strengthsSection(sections.getStrengths())
                .concernsSection(sections.getRisksAndGaps())
                .recommendationsSection(sections.getRecommendations())
                .isAiGenerated(isAiGenerated)
                .build();
        
        return learningReportRepository.save(report);
    }

    private LearningReportResponse.ReportSections parseSections(String content) {
        return LearningReportResponse.ReportSections.builder()
                .learningGoals(extractSection(content, "1. MỤC TIÊU HỌC TẬP", "2. KẾT QUẢ ĐẠT ĐƯỢC"))
                .achievements(extractSection(content, "2. KẾT QUẢ ĐẠT ĐƯỢC", "3. HÀNH VI HỌC TẬP"))
                .learningBehavior(extractSection(content, "3. HÀNH VI HỌC TẬP", "4. ĐIỂM MẠNH"))
                .strengths(extractSection(content, "4. ĐIỂM MẠNH", "5. ĐIỂM CẦN LƯU Ý"))
                .risksAndGaps(extractSection(content, "5. ĐIỂM CẦN LƯU Ý", "6. KHUYẾN NGHỊ CHO PHỤ HUYNH"))
                .recommendations(extractSection(content, "6. KHUYẾN NGHỊ CHO PHỤ HUYNH", null))
                .build();
    }

    private String extractSection(String content, String startMarker, String endMarker) {
        try {
            int startIdx = content.indexOf(startMarker);
            if (startIdx == -1) return "Không có dữ liệu";
            
            int endIdx = endMarker != null ? content.indexOf(endMarker) : content.length();
            if (endIdx == -1) endIdx = content.length();
            
            String section = content.substring(startIdx + startMarker.length(), endIdx).trim();
            // Remove leading ## if present
            if (section.startsWith("##")) {
                section = section.substring(2).trim();
            }
            return section.isEmpty() ? "Không có dữ liệu" : section;
        } catch (Exception e) {
            return "Không có dữ liệu";
        }
    }

    private LearningReportResponse generateFallbackReport(Long studentId, String studentName, 
            StudentOverviewDTO overview, List<RoadmapSessionSummary> roadmaps) {
        
        StringBuilder content = new StringBuilder();
        content.append("# 📊 BÁO CÁO HỌC TẬP\n\n");
        content.append("**Học viên:** ").append(studentName).append("\n\n");

        // Section 1
        content.append("## 1. MỤC TIÊU HỌC TẬP\n");
        if (roadmaps.isEmpty()) {
            content.append("- Học viên chưa tạo roadmap học tập nào.\n");
            content.append("- **Khuyến nghị:** Hãy hướng dẫn con tạo roadmap đầu tiên để có định hướng học tập rõ ràng.\n");
        } else {
            content.append("Các mục tiêu đang theo đuổi:\n");
            for (RoadmapSessionSummary r : roadmaps) {
                String goal = r.getValidatedGoal() != null ? r.getValidatedGoal() : 
                             (r.getOriginalGoal() != null ? r.getOriginalGoal() : r.getTitle());
                content.append("- **").append(goal != null ? goal : "Chưa đặt tên").append("**\n");
            }
        }

        // Section 2
        content.append("\n## 2. KẾT QUẢ ĐẠT ĐƯỢC\n");
        long completedRoadmaps = roadmaps.stream().filter(r -> r.getProgressPercentage() != null && r.getProgressPercentage() >= 100).count();
        long inProgress = roadmaps.stream().filter(r -> r.getProgressPercentage() != null && r.getProgressPercentage() > 0 && r.getProgressPercentage() < 100).count();
        content.append("- Tổng roadmap: ").append(roadmaps.size()).append("\n");
        content.append("- Hoàn thành: ").append(completedRoadmaps).append("\n");
        content.append("- Đang thực hiện: ").append(inProgress).append("\n");

        // Section 3 - StudentOverviewDTO has flat structure
        content.append("\n## 3. HÀNH VI HỌC TẬP\n");
        content.append("- Thời gian học hôm nay: ").append(overview.getStudyTimeToday()).append(" phút\n");
        content.append("- Thời gian học tuần này: ").append(overview.getStudyTimeWeek()).append(" phút\n");
        content.append("- Streak: ").append(overview.getStreakDays()).append(" ngày liên tục\n");

        // Section 4
        content.append("\n## 4. ĐIỂM MẠNH\n");
        if (overview.getStreakDays() > 3) {
            content.append("- ✅ Duy trì streak tốt (").append(overview.getStreakDays()).append(" ngày)\n");
        }
        if (!roadmaps.isEmpty()) {
            content.append("- ✅ Đã chủ động tạo roadmap học tập\n");
        }
        if (overview.getChatSessionsCount() > 0) {
            content.append("- ✅ Tích cực sử dụng AI mentor hỗ trợ học\n");
        }

        // Section 5
        content.append("\n## 5. ĐIỂM CẦN LƯU Ý\n");
        if (overview.getStreakDays() == 0) {
            content.append("- ⚠️ Chưa có streak, cần học đều đặn hơn\n");
        }
        if (roadmaps.isEmpty()) {
            content.append("- ⚠️ Chưa có định hướng học tập rõ ràng (không có roadmap)\n");
        }

        // Section 6
        content.append("\n## 6. KHUYẾN NGHỊ CHO PHỤ HUYNH\n");
        content.append("- 💡 Hãy cùng con đặt mục tiêu học tập cụ thể mỗi tuần\n");
        content.append("- 💡 Khuyến khích con sử dụng AI mentor để giải đáp thắc mắc\n");
        content.append("- 💡 Theo dõi streak để duy trì thói quen học tập hàng ngày\n");

        String reportContent = content.toString();

        return LearningReportResponse.builder()
                .generatedAt(LocalDateTime.now())
                .studentId(studentId)
                .studentName(studentName)
                .reportContent(reportContent)
                .sections(LearningReportResponse.ReportSections.builder()
                        .learningGoals(extractSection(reportContent, "1. MỤC TIÊU HỌC TẬP", "2. KẾT QUẢ ĐẠT ĐƯỢC"))
                        .achievements(extractSection(reportContent, "2. KẾT QUẢ ĐẠT ĐƯỢC", "3. HÀNH VI HỌC TẬP"))
                        .learningBehavior(extractSection(reportContent, "3. HÀNH VI HỌC TẬP", "4. ĐIỂM MẠNH"))
                        .strengths(extractSection(reportContent, "4. ĐIỂM MẠNH", "5. ĐIỂM CẦN LƯU Ý"))
                        .risksAndGaps(extractSection(reportContent, "5. ĐIỂM CẦN LƯU Ý", "6. KHUYẾN NGHỊ CHO PHỤ HUYNH"))
                        .recommendations(extractSection(reportContent, "6. KHUYẾN NGHỊ CHO PHỤ HUYNH", null))
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningReportResponse> getLearningReportHistory(Long parentId, Long studentId) {
        // Verify link
        if (!linkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not linked to this student");
        }

        List<LearningReport> reports = learningReportRepository.findByParentIdAndStudentIdOrderByGeneratedAtDesc(parentId, studentId);
        
        return reports.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LearningReportResponse getLatestLearningReport(Long parentId, Long studentId) {
        // Verify link
        if (!linkRepository.existsByParentIdAndStudentId(parentId, studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not linked to this student");
        }

        return learningReportRepository.findFirstByParentIdAndStudentIdOrderByGeneratedAtDesc(parentId, studentId)
                .map(this::convertToResponse)
                .orElse(null);
    }

    private LearningReportResponse convertToResponse(LearningReport report) {
        return LearningReportResponse.builder()
                .id(report.getId())
                .generatedAt(report.getGeneratedAt())
                .studentId(report.getStudent().getId())
                .studentName(report.getStudentName())
                .reportContent(report.getReportContent())
                .sections(LearningReportResponse.ReportSections.builder()
                        .learningGoals(report.getGoalsSection())
                        .achievements(report.getResultsSection())
                        .learningBehavior(report.getBehaviorSection())
                        .strengths(report.getStrengthsSection())
                        .risksAndGaps(report.getConcernsSection())
                        .recommendations(report.getRecommendationsSection())
                        .build())
                .build();
    }
}
