package com.exe.skillverse_backend.meowl_chat_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterShortlist;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterShortlistRepository;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.TestResult;
import com.exe.skillverse_backend.journey_service.repository.AssessmentTestRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.repository.TestResultRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.MentorAvailabilityRepository;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlOnboardingContextResponse;
import com.exe.skillverse_backend.meowl_chat_service.entity.MeowlUserPreference;
import com.exe.skillverse_backend.meowl_chat_service.model.MeowlRoleMode;
import com.exe.skillverse_backend.meowl_chat_service.repository.MeowlUserPreferenceRepository;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MeowlRoleGuidanceService {

    private static final Set<Journey.JourneyStatus> ENTRY_TEST_PENDING_STATUSES = EnumSet.of(
            Journey.JourneyStatus.NOT_STARTED,
            Journey.JourneyStatus.ASSESSMENT_PENDING,
            Journey.JourneyStatus.TEST_IN_PROGRESS,
            Journey.JourneyStatus.EVALUATION_PENDING);

    private final UserRepository userRepository;
    private final MeowlUserPreferenceRepository preferenceRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecruiterShortlistRepository recruiterShortlistRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final CourseRepository courseRepository;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;
    private final AssessmentTestRepository assessmentTestRepository;
    private final TestResultRepository testResultRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Value
    @Builder
    public static class RoleGuidanceContext {
        boolean loggedIn;
        String language;
        Long userId;
        MeowlRoleMode activeRole;
        List<MeowlRoleMode> availableRoles;
        boolean onboardingSeen;
        LocalDateTime onboardingSeenAt;
        String welcomeMessage;
        String nextBestAction;
        List<String> whatYouCanDo;
        List<MeowlOnboardingContextResponse.QuickAction> quickActions;
        List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes;
        List<String> suggestedPrompts;
        Map<String, String> contextSummary;
        String promptSection;
    }

    @Transactional(readOnly = true)
    public RoleGuidanceContext resolveContext(Long userId, String language, String requestedRole) {
        String normalizedLanguage = normalizeLanguage(language);
        if (userId == null) {
            return buildGuestContext(normalizedLanguage);
        }

        Optional<User> userOptional = userRepository.findByIdWithRoles(userId);
        if (userOptional.isEmpty()) {
            return buildGuestContext(normalizedLanguage);
        }

        User user = userOptional.get();
        LinkedHashSet<MeowlRoleMode> availableRoles = resolveAvailableRoles(user);
        MeowlUserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> MeowlUserPreference.builder().userId(userId).build());

        MeowlRoleMode activeRole = resolveActiveRole(availableRoles, preference.getPreferredRoleMode(), requestedRole);
        return switch (activeRole) {
            case RECRUITER -> buildRecruiterContext(user, normalizedLanguage, availableRoles, preference);
            case MENTOR -> buildMentorContext(user, normalizedLanguage, availableRoles, preference);
            case LEARNER -> buildLearnerContext(user, normalizedLanguage, availableRoles, preference);
            case GENERAL -> buildGuestContext(normalizedLanguage);
        };
    }

    @Transactional(readOnly = true)
    public MeowlOnboardingContextResponse buildOnboardingResponse(Long userId, String language, String requestedRole) {
        RoleGuidanceContext context = resolveContext(userId, language, requestedRole);
        return MeowlOnboardingContextResponse.builder()
                .success(true)
                .language(context.getLanguage())
                .activeRole(context.getActiveRole().name())
                .availableRoles(context.getAvailableRoles().stream().map(Enum::name).toList())
                .roleSwitchEnabled(context.getAvailableRoles().size() > 1)
                .onboardingSeen(context.isOnboardingSeen())
                .onboardingSeenAt(context.getOnboardingSeenAt())
                .welcomeMessage(context.getWelcomeMessage())
                .nextBestAction(context.getNextBestAction())
                .whatYouCanDo(context.getWhatYouCanDo())
                .quickActions(context.getQuickActions())
                .suggestedPrompts(context.getSuggestedPrompts())
                .contextSummary(context.getContextSummary())
                .build();
    }

    @Transactional
    public void markOnboardingSeen(Long userId, String requestedRole) {
        if (userId == null) {
            return;
        }

        Optional<User> userOptional = userRepository.findByIdWithRoles(userId);
        if (userOptional.isEmpty()) {
            return;
        }

        LinkedHashSet<MeowlRoleMode> availableRoles = resolveAvailableRoles(userOptional.get());
        MeowlUserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> MeowlUserPreference.builder().userId(userId).build());

        MeowlRoleMode preferredRole = resolveActiveRole(availableRoles, preference.getPreferredRoleMode(), requestedRole);
        preference.setPreferredRoleMode(preferredRole);
        preference.setOnboardingSeen(true);
        preference.setOnboardingSeenAt(LocalDateTime.now());
        preferenceRepository.save(preference);
    }

    @Transactional
    public void saveRolePreference(Long userId, String requestedRole) {
        if (userId == null) {
            return;
        }

        Optional<User> userOptional = userRepository.findByIdWithRoles(userId);
        if (userOptional.isEmpty()) {
            return;
        }

        LinkedHashSet<MeowlRoleMode> availableRoles = resolveAvailableRoles(userOptional.get());
        MeowlRoleMode preferredRole = resolveActiveRole(availableRoles, null, requestedRole);
        MeowlUserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> MeowlUserPreference.builder().userId(userId).build());
        preference.setPreferredRoleMode(preferredRole);
        preferenceRepository.save(preference);
    }

    private RoleGuidanceContext buildGuestContext(String language) {
        boolean isVi = "vi".equals(language);
        List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes =
                buildMegaMenuRoutes(MeowlRoleMode.GENERAL, language);
        String welcome = isVi
                ? "Xin chào bạn, Meowl đây nè! Mình sẽ giúp bạn khám phá SkillVerse ngắn gọn, rõ ràng và luôn có bước tiếp theo cụ thể."
                : "Hi, I am Meowl. I can guide you through SkillVerse and suggest the next best action.";
        String nextAction = isVi
                ? "Bắt đầu từ Trợ Lý AI (/chatbot), Lộ Trình Học Tập (/roadmap), hoặc Khóa Học (/courses) trong mega-menu để làm quen với SkillVerse."
                : "Start from AI Assistant (/chatbot), Learning Roadmap (/roadmap), or Courses (/courses) in the mega-menu to explore SkillVerse.";

        List<String> whatYouCanDo = isVi
                ? List.of(
                        "Giới thiệu nhanh các tính năng chính phù hợp mục tiêu của bạn",
                        "Gợi ý lộ trình bắt đầu học hoặc định hướng nghề nghiệp",
                        "Đưa bạn đến đúng trang bằng CTA cụ thể để bắt đầu ngay")
                : List.of(
                        "Get a quick tour of SkillVerse core features",
                        "Receive practical learning or career starting guidance",
                        "Jump to relevant pages through concrete CTA actions");

        List<MeowlOnboardingContextResponse.QuickAction> actions = selectQuickActions(
                megaMenuRoutes,
                "chatbot",
                "roadmap",
                "courses",
                "jobs");

        List<String> prompts = isVi
                ? List.of(
                        "Giới thiệu nhanh các khu chính trong mega-menu của SkillVerse.",
                        "Tôi nên bắt đầu từ Trợ Lý AI, Roadmap hay Khóa Học?",
                        "Tóm tắt các tính năng học, mentor, portfolio và việc làm đang có trên SkillVerse.")
                : List.of(
                        "Give me a quick walkthrough of the main mega-menu areas on SkillVerse.",
                        "Should I start with AI Assistant, Roadmap, or Courses?",
                        "Summarize the current learning, mentorship, portfolio, and job features on SkillVerse.");

        Map<String, String> summary = Map.of("account", "guest");

        return RoleGuidanceContext.builder()
                .loggedIn(false)
                .language(language)
                .userId(null)
                .activeRole(MeowlRoleMode.GENERAL)
                .availableRoles(List.of(MeowlRoleMode.GENERAL))
                .onboardingSeen(false)
                .welcomeMessage(welcome)
                .nextBestAction(nextAction)
                .whatYouCanDo(whatYouCanDo)
                .quickActions(actions)
                .megaMenuRoutes(megaMenuRoutes)
                .suggestedPrompts(prompts)
                .contextSummary(summary)
                .promptSection(buildRolePromptSection(MeowlRoleMode.GENERAL, language, summary, nextAction, megaMenuRoutes))
                .build();
    }

    private RoleGuidanceContext buildRecruiterContext(
            User user,
            String language,
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlUserPreference preference) {
        boolean isVi = "vi".equals(language);
        Long userId = user.getId();
        List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes =
                buildMegaMenuRoutes(MeowlRoleMode.RECRUITER, language);

        boolean hasProfile = recruiterProfileRepository.existsByUserId(userId);
        ApplicationStatus profileStatus = recruiterProfileRepository.findByUserId(userId)
                .map(profile -> profile.getApplicationStatus())
                .orElse(null);

        List<JobPosting> jobs = jobPostingRepository.findByRecruiterProfileUserIdOrderByCreatedAtDesc(userId);
        long totalJobs = jobs.size();
        long openJobs = jobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN).count();
        long pendingJobs = jobs.stream().filter(j -> j.getStatus() == JobStatus.PENDING_APPROVAL).count();
        long draftJobs = jobs.stream().filter(j -> j.getStatus() == JobStatus.IN_PROGRESS).count();
        long totalApplicants = jobs.stream().mapToLong(j -> Optional.ofNullable(j.getApplicantCount()).orElse(0)).sum();
        long activeShortlist = recruiterShortlistRepository.countByRecruiterIdAndShortlistStatus(
                userId,
                RecruiterShortlist.ShortlistStatus.ACTIVE);
        Optional<UserSubscription> recruiterSubscription = userSubscriptionRepository.findActiveRecruiterSubscription(user);
        boolean hasRecruiterPremium = recruiterSubscription.isPresent();
        String recruiterPremiumPlan = recruiterSubscription
                .map(subscription -> subscription.getPlan().getPlanType().name())
                .orElse("NONE");

        String nextAction;
        if (!hasProfile) {
            nextAction = isVi
                    ? "Bắt đầu từ Việc Làm (/jobs). Nếu full flow tuyển dụng chưa mở, bạn cần hoàn thiện recruiter profile trước."
                    : "Start from Jobs (/jobs). If the full hiring flow is still locked, your recruiter profile still needs completion.";
        } else if (profileStatus == ApplicationStatus.PENDING) {
            nextAction = isVi
                    ? "Mở Việc Làm (/jobs) để chuẩn bị JD thật rõ trong lúc hồ sơ recruiter đang chờ duyệt."
                    : "Open Jobs (/jobs) and prepare clear job descriptions while your recruiter profile is pending approval.";
        } else if (totalJobs == 0) {
            nextAction = isVi
                    ? "Mở Việc Làm (/jobs) để tạo và chuẩn bị job đầu tiên."
                    : "Open Jobs (/jobs) to create and prepare your first job.";
        } else if (openJobs > 0 && totalApplicants > 0) {
            nextAction = isVi
                    ? "Mở Việc Làm (/jobs) để review applicants và shortlist ứng viên phù hợp."
                    : "Open Jobs (/jobs) to review applicants and shortlist strong candidates.";
        } else if (openJobs == 0 && pendingJobs > 0) {
            nextAction = isVi
                    ? "Tiếp tục theo dõi Việc Làm (/jobs) để giữ pipeline tuyển dụng luôn sẵn."
                    : "Keep tracking Jobs (/jobs) so your hiring pipeline stays ready.";
        } else {
            nextAction = isVi
                    ? "Dùng Việc Làm (/jobs) để rà lại pipeline tuyển dụng, rồi dùng Cộng Đồng (/community) nếu cần mở rộng kết nối."
                    : "Use Jobs (/jobs) to review your hiring pipeline, then Community (/community) if you need wider outreach.";
        }

        String welcome = isVi
                ? "Chào bạn, Meowl đang ở chế độ Recruiter Assistant nè. Mình sẽ hỗ trợ vận hành tuyển dụng theo từng bước: job pipeline, applicants, candidate sourcing và shortlist."
                : "You are now in Recruiter Assistant mode. I will guide hiring operations step by step: job pipeline, applicants, candidate sourcing, and shortlist.";

        List<String> whatYouCanDo = isVi
                ? List.of(
                        "Hướng dẫn tạo và tối ưu job posting theo nhu cầu tuyển dụng",
                        "Gợi ý quy trình xử lý applicants và shortlist",
                        "Đề xuất bước tiếp theo dựa trên trạng thái job hiện tại")
                : List.of(
                        "Guide job posting setup and optimization for real hiring needs",
                        "Recommend practical applicant review and shortlist workflow",
                        "Suggest next steps based on your current job pipeline");

        List<MeowlOnboardingContextResponse.QuickAction> actions = selectQuickActions(
                megaMenuRoutes,
                "jobs",
                "community");

        List<String> prompts = isVi
                ? List.of(
                        "Trong role Recruiter, tôi nên bắt đầu từ Việc Làm hay Cộng Đồng?",
                        "Hướng dẫn tôi dùng Việc Làm để theo dõi job, applicants và shortlist.",
                        "Tôi muốn hiểu nhanh các tính năng tuyển dụng hiện có của SkillVerse.")
                : List.of(
                        "In Recruiter mode, should I start from Jobs or Community?",
                        "Guide me to use Jobs for postings, applicants, and shortlist flow.",
                        "Summarize the current hiring features available on SkillVerse.");

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("profile_ready", String.valueOf(hasProfile));
        summary.put("profile_status", profileStatus != null ? profileStatus.name() : "MISSING");
        summary.put("jobs_total", String.valueOf(totalJobs));
        summary.put("jobs_open", String.valueOf(openJobs));
        summary.put("jobs_pending_approval", String.valueOf(pendingJobs));
        summary.put("jobs_draft", String.valueOf(draftJobs));
        summary.put("applicants_total", String.valueOf(totalApplicants));
        summary.put("shortlist_active", String.valueOf(activeShortlist));
        summary.put("premium_active", String.valueOf(hasRecruiterPremium));
        summary.put("premium_plan", recruiterPremiumPlan);

        return RoleGuidanceContext.builder()
                .loggedIn(true)
                .language(language)
                .userId(userId)
                .activeRole(MeowlRoleMode.RECRUITER)
                .availableRoles(new ArrayList<>(availableRoles))
                .onboardingSeen(preference.isOnboardingSeen())
                .onboardingSeenAt(preference.getOnboardingSeenAt())
                .welcomeMessage(welcome)
                .nextBestAction(nextAction)
                .whatYouCanDo(whatYouCanDo)
                .quickActions(actions)
                .megaMenuRoutes(megaMenuRoutes)
                .suggestedPrompts(prompts)
                .contextSummary(summary)
                .promptSection(buildRolePromptSection(MeowlRoleMode.RECRUITER, language, summary, nextAction, megaMenuRoutes))
                .build();
    }

    private RoleGuidanceContext buildMentorContext(
            User user,
            String language,
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlUserPreference preference) {
        boolean isVi = "vi".equals(language);
        Long userId = user.getId();
        List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes =
                buildMegaMenuRoutes(MeowlRoleMode.MENTOR, language);

        boolean hasProfile = mentorProfileRepository.existsByUserId(userId);
        ApplicationStatus profileStatus = mentorProfileRepository.findByUserId(userId)
                .map(profile -> profile.getApplicationStatus())
                .orElse(null);

        long totalCourses = courseRepository.countByAuthorId(userId);
        long draftCourses = courseRepository.countByAuthorIdAndStatus(userId, CourseStatus.DRAFT);
        long pendingCourses = courseRepository.countByAuthorIdAndStatus(userId, CourseStatus.PENDING);
        long publicCourses = courseRepository.countByAuthorIdAndStatus(userId, CourseStatus.PUBLIC);
        int availabilitySlots = mentorAvailabilityRepository.findByMentorId(userId).size();
        long confirmedBookings = bookingRepository.countByMentorAndStatus(user, BookingStatus.CONFIRMED);
        long ongoingBookings = bookingRepository.countByMentorAndStatus(user, BookingStatus.ONGOING);

        String nextAction;
        if (!hasProfile) {
            nextAction = isVi
                    ? "Mở Hồ Sơ (/profile/mentor) để hoàn thiện mentor profile."
                    : "Open Profile (/profile/mentor) to complete your mentor profile.";
        } else if (totalCourses == 0) {
            nextAction = isVi
                    ? "Mở Khóa Học (/courses) để bắt đầu course đầu tiên."
                    : "Open Courses (/courses) to start your first course.";
        } else if (publicCourses == 0 && pendingCourses == 0) {
            nextAction = isVi
                    ? "Tiếp tục ở Khóa Học (/courses) để hoàn thiện và submit course đầu tiên."
                    : "Stay in Courses (/courses) to finish and submit your first course.";
        } else if (availabilitySlots == 0) {
            nextAction = isVi
                    ? "Mở Cố Vấn (/mentorship) để thiết lập lịch mentoring."
                    : "Open Mentorship (/mentorship) to configure your mentoring availability.";
        } else {
            nextAction = isVi
                    ? "Theo dõi Cố Vấn (/mentorship) và Khóa Học (/courses) để tối ưu booking và nội dung."
                    : "Track Mentorship (/mentorship) and Courses (/courses) to optimize bookings and content.";
        }

        String welcome = isVi
                ? "Chào mentor, Meowl đang ở chế độ Mentor Assistant nè. Mình sẽ hướng dẫn theo flow vận hành thực tế: profile, course, publish, availability và booking."
                : "You are now in Mentor Assistant mode. I will guide practical operations: profile, courses, publish flow, availability, and bookings.";

        List<String> whatYouCanDo = isVi
                ? List.of(
                        "Hướng dẫn tạo course và chuẩn bị publish đúng luồng",
                        "Gợi ý setup availability và booking theo tình trạng hiện tại",
                        "Đề xuất hành động tiếp theo để tăng số learner được hỗ trợ")
                : List.of(
                        "Guide course creation and publish-ready workflow",
                        "Recommend availability and booking setup from your current state",
                        "Suggest next actions to support more learners effectively");

        List<MeowlOnboardingContextResponse.QuickAction> actions = selectQuickActions(
                megaMenuRoutes,
                "courses",
                "mentorship",
                "profile-mentor",
                "chatbot",
                "community");

        List<String> prompts = isVi
                ? List.of(
                        "Trong role Mentor, tôi nên bắt đầu từ Khóa Học hay Cố Vấn?",
                        "Hướng dẫn tôi dùng Khóa Học để chuẩn bị course đầu tiên.",
                        "Tôi muốn mở lịch mentoring, nên đi từ Cố Vấn như thế nào?")
                : List.of(
                        "In Mentor mode, should I start from Courses or Mentorship?",
                        "Guide me to use Courses to prepare my first course.",
                        "I want to open mentoring availability. How should I use Mentorship?");

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("profile_ready", String.valueOf(hasProfile));
        summary.put("profile_status", profileStatus != null ? profileStatus.name() : "MISSING");
        summary.put("courses_total", String.valueOf(totalCourses));
        summary.put("courses_draft", String.valueOf(draftCourses));
        summary.put("courses_pending", String.valueOf(pendingCourses));
        summary.put("courses_public", String.valueOf(publicCourses));
        summary.put("availability_slots", String.valueOf(availabilitySlots));
        summary.put("bookings_confirmed", String.valueOf(confirmedBookings));
        summary.put("bookings_ongoing", String.valueOf(ongoingBookings));

        return RoleGuidanceContext.builder()
                .loggedIn(true)
                .language(language)
                .userId(userId)
                .activeRole(MeowlRoleMode.MENTOR)
                .availableRoles(new ArrayList<>(availableRoles))
                .onboardingSeen(preference.isOnboardingSeen())
                .onboardingSeenAt(preference.getOnboardingSeenAt())
                .welcomeMessage(welcome)
                .nextBestAction(nextAction)
                .whatYouCanDo(whatYouCanDo)
                .quickActions(actions)
                .megaMenuRoutes(megaMenuRoutes)
                .suggestedPrompts(prompts)
                .contextSummary(summary)
                .promptSection(buildRolePromptSection(MeowlRoleMode.MENTOR, language, summary, nextAction, megaMenuRoutes))
                .build();
    }

    private RoleGuidanceContext buildLearnerContext(
            User user,
            String language,
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlUserPreference preference) {
        boolean isVi = "vi".equals(language);
        Long userId = user.getId();
        List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes =
                buildMegaMenuRoutes(MeowlRoleMode.LEARNER, language);

        List<Journey> journeys = journeyRepository.findByUser(user).stream()
                .sorted(Comparator.comparing(Journey::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        long totalJourneys = journeys.size();
        long activeJourneys = journeyRepository.findActiveJourneysByUser(user).size();
        Journey latestJourney = journeys.isEmpty() ? null : journeys.get(0);
        AssessmentTest latestTest = latestJourney != null
                ? assessmentTestRepository.findTopByJourneyOrderByCreatedAtDesc(latestJourney).orElse(null)
                : null;
        TestResult latestResult = latestJourney != null
                ? testResultRepository.findTopByJourneyOrderByCreatedAtDesc(latestJourney).orElse(null)
                : null;
        Optional<UserSubscription> learnerSubscription = userSubscriptionRepository.findCurrentActiveSubscription(user);
        boolean learnerPremiumActive = learnerSubscription
                .map(subscription -> subscription.getPlan().getPlanType() != PremiumPlan.PlanType.FREE_TIER)
                .orElse(false);
        String learnerPremiumPlan = learnerSubscription
                .map(subscription -> subscription.getPlan().getPlanType().name())
                .orElse("NONE");

        String nextAction;
        if (latestJourney == null) {
            nextAction = isVi
                    ? "Mở Hành Trình (/journey) để bắt đầu bài test đầu vào và tạo roadmap cá nhân hóa."
                    : "Open Journey (/journey) to start the entry assessment and create your personalized roadmap.";
        } else if (latestResult == null && latestJourney.getStatus() != null && ENTRY_TEST_PENDING_STATUSES.contains(latestJourney.getStatus())) {
            nextAction = isVi
                    ? "Quay lại Hành Trình (/journey) để hoàn thành assessment và nhận kết quả kỹ năng."
                    : "Return to Journey (/journey) to finish the assessment and receive your skill result.";
        } else if (latestJourney.getRoadmapSessionId() == null) {
            nextAction = isVi
                    ? "Tiếp tục từ Hành Trình (/journey) để tạo roadmap từ kết quả assessment."
                    : "Continue in Journey (/journey) to generate the roadmap from your assessment result.";
        } else {
            nextAction = isVi
                    ? "Mở Lộ Trình Học Tập (/roadmap), rồi dùng Khóa Học (/courses) hoặc Cố Vấn (/mentorship) theo skill gap hiện tại."
                    : "Open Learning Roadmap (/roadmap), then use Courses (/courses) or Mentorship (/mentorship) based on your current skill gaps.";
        }

        String welcome = isVi
                ? "Chào bạn, Meowl đang ở chế độ Learner Assistant nè. Mình sẽ giúp bạn đi từng bước từ bài test đầu vào đến roadmap, course và mentor."
                : "You are now in Learner Assistant mode. I will guide you step by step from entry assessment to roadmap, courses, and mentors.";

        List<String> whatYouCanDo = isVi
                ? List.of(
                        "Hướng dẫn rõ từng bước tạo và làm bài test đầu vào",
                        "Giải thích kết quả assessment và cách tạo roadmap cá nhân hóa",
                        "Đề xuất course hoặc mentor và hành động tiếp theo theo skill gap")
                : List.of(
                        "Provide clear step-by-step entry assessment guidance",
                        "Explain assessment results and roadmap creation flow",
                        "Recommend courses or mentors and next actions from your skill gaps");

        List<MeowlOnboardingContextResponse.QuickAction> actions = selectQuickActions(
                megaMenuRoutes,
                "journey",
                "dashboard",
                "roadmap",
                "chatbot",
                "study-planner",
                "courses");

        List<String> prompts = isVi
                ? List.of(
                        "Hướng dẫn tôi bắt đầu từ Hành Trình và Lộ Trình Học Tập.",
                        "Tôi nên dùng Dashboard, Roadmap và Kế Hoạch AI theo thứ tự nào?",
                        "Tôi muốn chuyển từ học sang portfolio và việc làm thì nên đi trong mega-menu ra sao?")
                : List.of(
                        "Guide me to start from Journey and Learning Roadmap.",
                        "What order should I use Dashboard, Roadmap, and Study Planner?",
                        "How should I move from learning into portfolio and jobs through the mega-menu?");

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("journeys_total", String.valueOf(totalJourneys));
        summary.put("journeys_active", String.valueOf(activeJourneys));
        summary.put("latest_journey_status", latestJourney != null && latestJourney.getStatus() != null
                ? latestJourney.getStatus().name()
                : "NONE");
        summary.put("latest_test_status", latestTest != null && latestTest.getStatus() != null
                ? latestTest.getStatus().name()
                : "NONE");
        summary.put("latest_result_available", String.valueOf(latestResult != null));
        summary.put("roadmap_ready", String.valueOf(latestJourney != null && latestJourney.getRoadmapSessionId() != null));
        summary.put("premium_active", String.valueOf(learnerPremiumActive));
        summary.put("premium_plan", learnerPremiumPlan);

        return RoleGuidanceContext.builder()
                .loggedIn(true)
                .language(language)
                .userId(userId)
                .activeRole(MeowlRoleMode.LEARNER)
                .availableRoles(new ArrayList<>(availableRoles))
                .onboardingSeen(preference.isOnboardingSeen())
                .onboardingSeenAt(preference.getOnboardingSeenAt())
                .welcomeMessage(welcome)
                .nextBestAction(nextAction)
                .whatYouCanDo(whatYouCanDo)
                .quickActions(actions)
                .megaMenuRoutes(megaMenuRoutes)
                .suggestedPrompts(prompts)
                .contextSummary(summary)
                .promptSection(buildRolePromptSection(MeowlRoleMode.LEARNER, language, summary, nextAction, megaMenuRoutes))
                .build();
    }

    private String buildRolePromptSection(
            MeowlRoleMode role,
            String language,
            Map<String, String> contextSummary,
            String nextBestAction,
            List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes) {
        boolean isVi = "vi".equals(language);
        StringBuilder section = new StringBuilder();

        section.append(isVi ? "=== CHE DO HUONG DAN THEO VAI TRO ===\n" : "=== ROLE-AWARE GUIDANCE MODE ===\n");
        section.append(isVi ? "Vai tro hien tai: " : "Resolved role: ").append(role.name()).append('\n');
        section.append(isVi
                ? "Phong cach bat buoc: ro rang, than thien, chuyen nghiep; uu tien hanh dong; tranh dai dong; luon chot buoc tiep theo cu the.\n"
                : "Required style: clear, friendly, professional; action-first; avoid long explanations; always end with one concrete next step.\n");
        section.append(isVi
                ? "Khong duoc bịa tinh nang. Neu thieu du lieu, hay noi ro gioi han va dua ra buoc hanh dong kha thi.\n"
                : "Do not invent features. If data is missing, state the limit clearly and propose a practical action.\n");
        section.append(isVi
                ? "Nguon route duoc phep la mega-menu cua frontend cho vai tro hien tai. Chi duoc goi y, gan link, CTA, hoac huong dan click vao cac route nam trong danh sach hop le ben duoi.\n"
                : "The source of truth for allowed routes is the frontend mega-menu for the current role. Only suggest, link, CTA, or tell users to click routes that appear in the allowed list below.\n");
        section.append(isVi
                ? "Tuyet doi khong dieu huong sang route cua role khac hoac route an/noi bo nhu /business, /business/premium, /mentor, /mentor/courses/create, /journey/create, /premium, /chatbot/general, /chatbot/expert, /profile/business, /profile/user, /wallet, /my-wallet, /my-bookings, /business/contracts, /my-contracts, /messages, /notifications.\n"
                : "Never route users to another role's pages or hidden/internal routes such as /business, /business/premium, /mentor, /mentor/courses/create, /journey/create, /premium, /chatbot/general, /chatbot/expert, /profile/business, /profile/user, /wallet, /my-wallet, /my-bookings, /business/contracts, /my-contracts, /messages, or /notifications.\n");
        section.append(isVi
                ? "Khong gioi thieu seminar, gamification, parent, hoac admin trong phien ban huong dan nay.\n"
                : "Do not recommend seminar, gamification, parent, or admin areas as part of this assistant scope.\n");
        section.append(isVi
                ? "Neu user hoi ve mot tinh nang co that nhung route cua no khong nam trong mega-menu cua role hien tai, hay giai thich tong quan ma KHONG neu route do.\n"
                : "If the user asks about a real feature whose route is outside the current role's mega-menu, explain the feature at a high level without naming that route.\n");

        if (role == MeowlRoleMode.LEARNER) {
            section.append(isVi
                    ? "Voi learner: uu tien flow test dau vao, Journey, roadmap, study planner, khoa hoc, mentor, portfolio, va cong viec. Khi duoc hoi onboarding, hay noi ro: buoc bat dau, ly do, ket qua nhan duoc, va buoc tiep theo.\n"
                    : "For learners: prioritize entry assessment, Journey, roadmap, study planner, courses, mentors, portfolio, and jobs. When onboarding is requested, explain the start point, why it matters, the outcome, and the next step.\n");
        } else if (role == MeowlRoleMode.RECRUITER) {
            section.append(isVi
                    ? "Voi recruiter: noi theo boi canh van hanh tuyen dung thuc te (job pipeline, applicants, shortlist, candidate sourcing), khong mo ta chung chung.\n"
                    : "For recruiters: speak in practical hiring operations context (job pipeline, applicants, shortlist, candidate sourcing), not generic feature descriptions.\n");
        } else if (role == MeowlRoleMode.MENTOR) {
            section.append(isVi
                    ? "Voi mentor: huong dan theo trinh tu van hanh thuc te (profile -> course -> publish -> availability -> booking) nhung chi gan route thuoc mega-menu hien tai.\n"
                    : "For mentors: guide in practical sequence (profile -> course -> publish -> availability -> booking), but only attach routes that belong to the current mega-menu.\n");
        }

        section.append(isVi ? "Route hop le trong mega-menu cua role nay:\n" : "Allowed mega-menu routes for this role:\n");
        if (megaMenuRoutes != null) {
            megaMenuRoutes.forEach(route -> section.append("- ")
                    .append(route.getLabel())
                    .append(": ")
                    .append(route.getActionValue())
                    .append(" - ")
                    .append(route.getDescription())
                    .append('\n'));
        }

        section.append(isVi ? "Ngu canh user hien tai:\n" : "Current user context:\n");
        if (role == MeowlRoleMode.LEARNER || role == MeowlRoleMode.RECRUITER) {
            section.append(isVi
                    ? "Khi user hoi ve Premium, chi neu dung quyen loi dang co trong he thong va giai thich ngan gon theo use-case thuc te, nhung khong day route Premium neu route do khong nam trong mega-menu.\n"
                    : "When users ask about Premium, mention only existing in-system benefits and explain briefly with practical use cases, but do not push a Premium route when it is outside the mega-menu.\n");
        }
        contextSummary.forEach((k, v) -> section.append("- ").append(k).append(": ").append(v).append('\n'));
        section.append(isVi ? "Buoc tiep theo:\n- " : "Next best action:\n- ").append(nextBestAction).append('\n');
        return section.toString();
    }

    private String buildPromptSection(
            MeowlRoleMode role,
            String language,
            Map<String, String> contextSummary,
            String nextBestAction,
            List<MeowlOnboardingContextResponse.QuickAction> megaMenuRoutes) {
        return buildRolePromptSection(role, language, contextSummary, nextBestAction, megaMenuRoutes);
    }

    private List<MeowlOnboardingContextResponse.QuickAction> buildMegaMenuRoutes(
            MeowlRoleMode role,
            String language) {
        boolean isVi = "vi".equals(language);
        return switch (role) {
            case RECRUITER -> isVi
                    ? List.of(
                            quickAction("jobs", "Việc Làm", "Tìm kiếm và quản lý tin tuyển dụng", "NAVIGATE", "/jobs"),
                            quickAction("community", "Cộng Đồng", "Tham gia cộng đồng học tập sôi động", "NAVIGATE", "/community"))
                    : List.of(
                            quickAction("jobs", "Jobs", "Search and manage job postings", "NAVIGATE", "/jobs"),
                            quickAction("community", "Community", "Join the active learning community", "NAVIGATE", "/community"));
            case MENTOR -> isVi
                    ? List.of(
                            quickAction("courses", "Khóa Học", "Quản lý nội dung khóa học của bạn", "NAVIGATE", "/courses"),
                            quickAction("community", "Cộng Đồng", "Tương tác cùng cộng đồng học tập", "NAVIGATE", "/community"),
                            quickAction("mentorship", "Cố Vấn", "Quản lý hồ sơ và lịch mentoring", "NAVIGATE", "/mentorship"),
                            quickAction("profile-mentor", "Hồ Sơ", "Quản lý hồ sơ mentor của bạn", "NAVIGATE", "/profile/mentor"),
                            quickAction("chatbot", "Trợ Lý AI", "Nhận hỗ trợ từ trợ lý AI", "NAVIGATE", "/chatbot"))
                    : List.of(
                            quickAction("courses", "Courses", "Manage your course content", "NAVIGATE", "/courses"),
                            quickAction("community", "Community", "Interact with the learning community", "NAVIGATE", "/community"),
                            quickAction("mentorship", "Mentorship", "Manage mentoring profile and schedule", "NAVIGATE", "/mentorship"),
                            quickAction("profile-mentor", "Profile", "Manage your mentor profile", "NAVIGATE", "/profile/mentor"),
                            quickAction("chatbot", "AI Assistant", "Get support from Meowl", "NAVIGATE", "/chatbot"));
            case LEARNER -> isVi
                    ? List.of(
                            quickAction("journey", "Hành Trình", "Bắt đầu hoặc tiếp tục hành trình học tập", "NAVIGATE", "/journey"),
                            quickAction("dashboard", "Bảng Điều Khiển", "Theo dõi tiến độ học tập và thành tích", "NAVIGATE", "/dashboard"),
                            quickAction("roadmap", "Lộ Trình Học Tập", "Khám phá lộ trình học tập và kỹ năng", "NAVIGATE", "/roadmap"),
                            quickAction("chatbot", "Trợ Lý AI", "Nhận hỗ trợ từ trợ lý AI", "NAVIGATE", "/chatbot"),
                            quickAction("study-planner", "Kế Hoạch AI", "Quản lý và tiếp tục kế hoạch học tập cá nhân", "NAVIGATE", "/study-planner"),
                            quickAction("courses", "Khóa Học", "Khám phá các khóa học chất lượng cao", "NAVIGATE", "/courses"),
                            quickAction("mentorship", "Cố Vấn", "Kết nối với chuyên gia trong ngành", "NAVIGATE", "/mentorship"),
                            quickAction("community", "Cộng Đồng", "Tham gia cộng đồng học tập sôi động", "NAVIGATE", "/community"),
                            quickAction("meowl-shop", "Meowl Shop", "Cửa hàng skin Meowl", "NAVIGATE", "/meowl-shop"),
                            quickAction("portfolio", "Portfolio", "Quản lý và chia sẻ thành tích của bạn", "NAVIGATE", "/portfolio"),
                            quickAction("my-applications", "Trung Tâm Công Việc", "Quản lý toàn bộ đơn ứng tuyển của bạn", "NAVIGATE", "/my-applications"),
                            quickAction("jobs", "Việc Làm", "Tìm kiếm cơ hội việc làm phù hợp", "NAVIGATE", "/jobs"))
                    : List.of(
                            quickAction("journey", "Journey", "Start or continue your learning journey", "NAVIGATE", "/journey"),
                            quickAction("dashboard", "Dashboard", "Track learning progress and achievements", "NAVIGATE", "/dashboard"),
                            quickAction("roadmap", "Learning Roadmap", "Explore your skill roadmap", "NAVIGATE", "/roadmap"),
                            quickAction("chatbot", "AI Assistant", "Get support from Meowl", "NAVIGATE", "/chatbot"),
                            quickAction("study-planner", "Study Planner", "Manage your personal study plan", "NAVIGATE", "/study-planner"),
                            quickAction("courses", "Courses", "Explore available courses", "NAVIGATE", "/courses"),
                            quickAction("mentorship", "Mentorship", "Connect with mentors", "NAVIGATE", "/mentorship"),
                            quickAction("community", "Community", "Join the learning community", "NAVIGATE", "/community"),
                            quickAction("meowl-shop", "Meowl Shop", "Browse Meowl skins", "NAVIGATE", "/meowl-shop"),
                            quickAction("portfolio", "Portfolio", "Showcase your achievements", "NAVIGATE", "/portfolio"),
                            quickAction("my-applications", "Job Hub", "Manage your applications and work hub", "NAVIGATE", "/my-applications"),
                            quickAction("jobs", "Jobs", "Find matching job opportunities", "NAVIGATE", "/jobs"));
            case GENERAL -> isVi
                    ? List.of(
                            quickAction("chatbot", "Trợ Lý AI", "Nhận hỗ trợ từ trợ lý AI", "NAVIGATE", "/chatbot"),
                            quickAction("study-planner", "Kế Hoạch AI", "Xem khu kế hoạch học tập", "NAVIGATE", "/study-planner"),
                            quickAction("roadmap", "Lộ Trình Học Tập", "Khám phá lộ trình học tập", "NAVIGATE", "/roadmap"),
                            quickAction("courses", "Khóa Học", "Khám phá khóa học đang có", "NAVIGATE", "/courses"),
                            quickAction("mentorship", "Cố Vấn", "Xem khu mentorship", "NAVIGATE", "/mentorship"),
                            quickAction("community", "Cộng Đồng", "Xem cộng đồng học tập", "NAVIGATE", "/community"),
                            quickAction("jobs", "Việc Làm", "Khám phá cơ hội việc làm", "NAVIGATE", "/jobs"),
                            quickAction("portfolio", "Portfolio", "Xem khu portfolio", "NAVIGATE", "/portfolio"),
                            quickAction("meowl-shop", "Meowl Shop", "Khám phá cửa hàng skin Meowl", "NAVIGATE", "/meowl-shop"))
                    : List.of(
                            quickAction("chatbot", "AI Assistant", "Get help from Meowl", "NAVIGATE", "/chatbot"),
                            quickAction("study-planner", "Study Planner", "Open the study planning area", "NAVIGATE", "/study-planner"),
                            quickAction("roadmap", "Learning Roadmap", "Explore learning roadmap", "NAVIGATE", "/roadmap"),
                            quickAction("courses", "Courses", "Browse available courses", "NAVIGATE", "/courses"),
                            quickAction("mentorship", "Mentorship", "Open the mentorship area", "NAVIGATE", "/mentorship"),
                            quickAction("community", "Community", "Open the learning community", "NAVIGATE", "/community"),
                            quickAction("jobs", "Jobs", "Explore job opportunities", "NAVIGATE", "/jobs"),
                            quickAction("portfolio", "Portfolio", "Open the portfolio area", "NAVIGATE", "/portfolio"),
                            quickAction("meowl-shop", "Meowl Shop", "Explore Meowl skins", "NAVIGATE", "/meowl-shop"));
        };
    }

    private List<MeowlOnboardingContextResponse.QuickAction> selectQuickActions(
            List<MeowlOnboardingContextResponse.QuickAction> routes,
            String... ids) {
        Map<String, MeowlOnboardingContextResponse.QuickAction> byId = new LinkedHashMap<>();
        if (routes != null) {
            routes.forEach(route -> byId.put(route.getId(), route));
        }

        List<MeowlOnboardingContextResponse.QuickAction> selected = new ArrayList<>();
        for (String id : ids) {
            MeowlOnboardingContextResponse.QuickAction action = byId.get(id);
            if (action != null) {
                selected.add(action);
            }
        }
        return selected;
    }

    private LinkedHashSet<MeowlRoleMode> resolveAvailableRoles(User user) {
        LinkedHashSet<MeowlRoleMode> roles = new LinkedHashSet<>();
        for (Role role : user.getRoles()) {
            String roleName = role.getName() != null
                    ? role.getName().trim().toUpperCase(Locale.ROOT)
                    : "";
            switch (roleName) {
                case "RECRUITER" -> roles.add(MeowlRoleMode.RECRUITER);
                case "MENTOR" -> roles.add(MeowlRoleMode.MENTOR);
                case "USER" -> roles.add(MeowlRoleMode.LEARNER);
                default -> {
                }
            }
        }
        if (roles.isEmpty()) {
            roles.add(MeowlRoleMode.LEARNER);
        }
        return roles;
    }

    private MeowlRoleMode resolveActiveRole(
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlRoleMode preferredRole,
            String requestedRole) {
        Optional<MeowlRoleMode> requested = MeowlRoleMode.fromRaw(requestedRole);
        if (requested.isPresent() && availableRoles.contains(requested.get())) {
            return requested.get();
        }
        if (preferredRole != null && availableRoles.contains(preferredRole)) {
            return preferredRole;
        }
        if (availableRoles.contains(MeowlRoleMode.RECRUITER)) {
            return MeowlRoleMode.RECRUITER;
        }
        if (availableRoles.contains(MeowlRoleMode.MENTOR)) {
            return MeowlRoleMode.MENTOR;
        }
        if (availableRoles.contains(MeowlRoleMode.LEARNER)) {
            return MeowlRoleMode.LEARNER;
        }
        return MeowlRoleMode.GENERAL;
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "en";
        }
        return "vi".equalsIgnoreCase(language) ? "vi" : "en";
    }

    private MeowlOnboardingContextResponse.QuickAction quickAction(
            String id,
            String label,
            String description,
            String actionType,
            String actionValue) {
        return MeowlOnboardingContextResponse.QuickAction.builder()
                .id(id)
                .label(label)
                .description(description)
                .actionType(actionType)
                .actionValue(actionValue)
                .build();
    }
}

