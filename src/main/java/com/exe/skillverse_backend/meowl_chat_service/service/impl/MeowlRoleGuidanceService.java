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
        String welcome = isVi
                ? "Xin chào bạn, Meowl đây nè! Mình sẽ giúp bạn khám phá SkillVerse ngắn gọn, rõ ràng và luôn có bước tiếp theo cụ thể."
                : "Hi, I am Meowl. I can guide you through SkillVerse and suggest the next best action.";
        String nextAction = isVi
                ? "Đăng nhập để Meowl hỗ trợ đúng vai trò Learner, Mentor hoặc Recruiter nhé."
                : "Sign in so Meowl can guide you based on Learner, Mentor, or Recruiter mode.";

        List<String> whatYouCanDo = isVi
                ? List.of(
                        "Giới thiệu nhanh các tính năng chính phù hợp mục tiêu của bạn",
                        "Gợi ý lộ trình bắt đầu học hoặc định hướng nghề nghiệp",
                        "Đưa bạn đến đúng trang bằng CTA cụ thể để bắt đầu ngay")
                : List.of(
                        "Get a quick tour of SkillVerse core features",
                        "Receive practical learning or career starting guidance",
                        "Jump to relevant pages through concrete CTA actions");

        List<MeowlOnboardingContextResponse.QuickAction> actions = isVi
                ? List.of(
                        quickAction("login", "Đăng nhập", "Mở trang đăng nhập để dùng trợ lý theo vai trò", "NAVIGATE", "/login"),
                        quickAction("courses", "Khám phá khóa học", "Xem các khóa học đang có trên SkillVerse", "NAVIGATE", "/courses"))
                : List.of(
                        quickAction("login", "Sign in", "Open login to use role-aware assistant", "NAVIGATE", "/login"),
                        quickAction("courses", "Explore courses", "Browse available courses on SkillVerse", "NAVIGATE", "/courses"));

        List<String> prompts = isVi
                ? List.of(
                        "Giới thiệu nhanh SkillVerse cho người mới bắt đầu.",
                        "Mình nên bắt đầu từ đâu để học hiệu quả trên SkillVerse?")
                : List.of(
                        "Give me a quick SkillVerse walkthrough for new users.",
                        "Where should I start learning on SkillVerse?");

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
                .suggestedPrompts(prompts)
                .contextSummary(summary)
                .promptSection(buildPromptSection(MeowlRoleMode.GENERAL, language, summary, nextAction))
                .build();
    }

    private RoleGuidanceContext buildRecruiterContext(
            User user,
            String language,
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlUserPreference preference) {
        boolean isVi = "vi".equals(language);
        Long userId = user.getId();

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
                    ? "Hoàn thiện hồ sơ recruiter tại /profile/business trước khi đăng job."
                    : "Complete your recruiter profile at /profile/business before posting jobs.";
        } else if (profileStatus == ApplicationStatus.PENDING) {
            nextAction = isVi
                    ? "Hồ sơ của bạn đang chờ duyệt. Trong lúc này, hãy chuẩn bị mô tả job thật rõ để đăng ngay khi được duyệt."
                    : "Your profile is pending approval. Prepare clear job descriptions so you can post immediately.";
        } else if (totalJobs == 0) {
            nextAction = isVi
                    ? "Tạo job đầu tiên trong Business dashboard để bắt đầu nhận ứng viên."
                    : "Create your first job in Business dashboard to start receiving applicants.";
        } else if (openJobs > 0 && totalApplicants > 0) {
            nextAction = isVi
                    ? "Ưu tiên xem applicants của các job đang mở và shortlist ứng viên phù hợp."
                    : "Prioritize reviewing applicants on open jobs and shortlist strong candidates.";
        } else if (openJobs == 0 && pendingJobs > 0) {
            nextAction = isVi
                    ? "Theo dõi các job đang chờ duyệt và chuẩn bị thêm job mới để giữ pipeline tuyển dụng."
                    : "Track pending jobs and prepare additional postings to keep your hiring pipeline moving.";
        } else {
            nextAction = isVi
                    ? "Dùng Candidate Search để chủ động tìm ứng viên phù hợp và thêm vào shortlist."
                    : "Use Candidate Search to proactively find suitable candidates and shortlist them.";
        }

        String welcome = isVi
                ? "Chào bạn, Meowl đang ở chế độ Recruiter Assistant nè. Mình sẽ hỗ trợ vận hành tuyển dụng theo từng bước: hồ sơ, job, applicants, candidate search và shortlist."
                : "You are now in Recruiter Assistant mode. I will guide hiring operations step by step: profile, jobs, applicants, candidate search, and shortlist.";

        List<String> whatYouCanDo = isVi
                ? List.of(
                        "Hướng dẫn tạo và tối ưu job posting theo nhu cầu tuyển dụng",
                        "Gợi ý quy trình xử lý applicants và shortlist",
                        "Đề xuất bước tiếp theo dựa trên trạng thái job hiện tại")
                : List.of(
                        "Guide job posting setup and optimization for real hiring needs",
                        "Recommend practical applicant review and shortlist workflow",
                        "Suggest next steps based on your current job pipeline");

        List<MeowlOnboardingContextResponse.QuickAction> actions = isVi
                ? List.of(
                        quickAction("recruiter-profile", "Hồ sơ Recruiter", "Mở hồ sơ business/recruiter", "NAVIGATE", "/profile/business"),
                        quickAction("create-job", "Tạo job đầu tiên", "Bắt đầu đăng tuyển trong Business dashboard", "NAVIGATE", "/business"),
                        quickAction("review-applicants", "Xem Applicants", "Kiểm tra ứng viên đã apply", "NAVIGATE", "/business"),
                        quickAction("search-candidates", "Tìm ứng viên", "Mở Candidate Search để source hồ sơ", "NAVIGATE", "/business"))
                : List.of(
                        quickAction("recruiter-profile", "Recruiter profile", "Open business/recruiter profile", "NAVIGATE", "/profile/business"),
                        quickAction("create-job", "Create first job", "Start posting from Business dashboard", "NAVIGATE", "/business"),
                        quickAction("review-applicants", "Review applicants", "Check candidate applications", "NAVIGATE", "/business"),
                        quickAction("search-candidates", "Search candidates", "Open Candidate Search for sourcing", "NAVIGATE", "/business"));

        List<String> prompts = isVi
                ? List.of(
                        "Hướng dẫn tôi tạo job đầu tiên từ đầu đến lúc publish.",
                        "Tôi nên xử lý applicants thế nào để shortlist nhanh?",
                        "Cho tôi checklist tuyển dụng tuần này theo trạng thái job hiện tại.")
                : List.of(
                        "Guide me to create and publish my first job posting.",
                        "What is the fastest way to review applicants and shortlist?",
                        "Give me a weekly hiring checklist from my current job statuses.");

        List<MeowlOnboardingContextResponse.QuickAction> recruiterActions = new ArrayList<>(actions);
        recruiterActions.add(isVi
                ? quickAction(
                        "recruiter-premium-benefits",
                        "Quyền lợi Premium",
                        "Xem quyền lợi recruiter premium và quota hiện tại",
                        "NAVIGATE",
                        "/business/premium")
                : quickAction(
                        "recruiter-premium-benefits",
                        "Premium benefits",
                        "View recruiter premium benefits and quotas",
                        "NAVIGATE",
                        "/business/premium"));

        List<String> recruiterPrompts = new ArrayList<>(prompts);
        recruiterPrompts.add(isVi
                ? (hasRecruiterPremium
                        ? "Tóm tắt nhanh quyền lợi gói premium recruiter tôi đang có và bước nên dùng ngay."
                        : "Giải thích quyền lợi Premium Recruiter (job boost, candidate search, analytics) và khi nào nên nâng cấp.")
                : (hasRecruiterPremium
                        ? "Summarize my active recruiter premium benefits and the best next action."
                        : "Explain Recruiter Premium benefits (job boost, candidate search, analytics) and when to upgrade."));

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
                .quickActions(recruiterActions)
                .suggestedPrompts(recruiterPrompts)
                .contextSummary(summary)
                .promptSection(buildPromptSection(MeowlRoleMode.RECRUITER, language, summary, nextAction))
                .build();
    }

    private RoleGuidanceContext buildMentorContext(
            User user,
            String language,
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlUserPreference preference) {
        boolean isVi = "vi".equals(language);
        Long userId = user.getId();

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
                    ? "Hoàn thiện mentor profile tại /profile/mentor để mở đầy đủ tính năng mentoring."
                    : "Complete your mentor profile at /profile/mentor to unlock mentoring operations.";
        } else if (totalCourses == 0) {
            nextAction = isVi
                    ? "Tạo course đầu tiên tại /mentor/courses/create để bắt đầu onboarding mentor."
                    : "Create your first course at /mentor/courses/create to start mentor onboarding.";
        } else if (publicCourses == 0 && pendingCourses == 0) {
            nextAction = isVi
                    ? "Chuẩn bị và submit ít nhất 1 course để có nội dung public cho learner."
                    : "Prepare and submit at least one course so learners can access your content.";
        } else if (availabilitySlots == 0) {
            nextAction = isVi
                    ? "Thiết lập availability trong Mentorship để learner có thể booking với bạn."
                    : "Set your availability in Mentorship so learners can book sessions.";
        } else {
            nextAction = isVi
                    ? "Theo dõi booking confirmed/ongoing và tối ưu course cùng lịch mentoring theo nhu cầu learner."
                    : "Track confirmed/ongoing bookings and optimize your courses and mentoring schedule.";
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

        List<MeowlOnboardingContextResponse.QuickAction> actions = isVi
                ? List.of(
                        quickAction("mentor-profile", "Hồ sơ Mentor", "Mở trang hồ sơ mentor", "NAVIGATE", "/profile/mentor"),
                        quickAction("create-course", "Tạo Course", "Vào course builder để tạo khóa học", "NAVIGATE", "/mentor/courses/create"),
                        quickAction("mentor-dashboard", "Quản lý Course", "Xem dashboard mentor và danh sách course", "NAVIGATE", "/mentor"),
                        quickAction("booking-setup", "Setup Booking", "Thiết lập lịch mentoring và booking", "NAVIGATE", "/mentorship"))
                : List.of(
                        quickAction("mentor-profile", "Mentor profile", "Open mentor profile page", "NAVIGATE", "/profile/mentor"),
                        quickAction("create-course", "Create course", "Open course builder to create a course", "NAVIGATE", "/mentor/courses/create"),
                        quickAction("mentor-dashboard", "Manage courses", "Open mentor dashboard and course list", "NAVIGATE", "/mentor"),
                        quickAction("booking-setup", "Setup booking", "Configure mentoring availability and booking", "NAVIGATE", "/mentorship"));

        List<String> prompts = isVi
                ? List.of(
                        "Hướng dẫn tôi trình tự từ tạo course đến publish.",
                        "Tôi đã có course nhưng chưa có booking, tôi nên làm gì tiếp?",
                        "Cho tôi checklist vận hành mentor tuần này.")
                : List.of(
                        "Guide the sequence from course creation to publish.",
                        "I have courses but no bookings yet. What should I do next?",
                        "Give me a practical weekly mentor operations checklist.");

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
                .suggestedPrompts(prompts)
                .contextSummary(summary)
                .promptSection(buildPromptSection(MeowlRoleMode.MENTOR, language, summary, nextAction))
                .build();
    }

    private RoleGuidanceContext buildLearnerContext(
            User user,
            String language,
            LinkedHashSet<MeowlRoleMode> availableRoles,
            MeowlUserPreference preference) {
        boolean isVi = "vi".equals(language);
        Long userId = user.getId();

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
                    ? "Bắt đầu bằng bài test đầu vào tại /journey/create để hệ thống tạo roadmap cá nhân hóa."
                    : "Start with the entry assessment at /journey/create so the system can generate your personalized roadmap.";
        } else if (latestResult == null && latestJourney.getStatus() != null && ENTRY_TEST_PENDING_STATUSES.contains(latestJourney.getStatus())) {
            nextAction = isVi
                    ? "Vào /journey để hoàn thành bài test đầu vào và nhận kết quả đánh giá kỹ năng."
                    : "Open /journey to complete your entry assessment and receive your skill evaluation.";
        } else if (latestJourney.getRoadmapSessionId() == null) {
            nextAction = isVi
                    ? "Từ kết quả test, tạo roadmap trong Journey để có lộ trình học rõ ràng theo level hiện tại."
                    : "From your test result, generate a roadmap in Journey for a clear level-based learning path.";
        } else {
            nextAction = isVi
                    ? "Tiếp tục học theo roadmap và chọn course hoặc mentor phù hợp với skill gap hiện tại."
                    : "Continue learning from your roadmap and choose courses/mentors based on current skill gaps.";
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
                        "Recommend courses/mentors and next actions from your skill gaps");

        List<MeowlOnboardingContextResponse.QuickAction> actions = isVi
                ? List.of(
                        quickAction("entry-test-start", "Tạo bài test đầu vào", "Bắt đầu flow Journey assessment", "NAVIGATE", "/journey/create"),
                        quickAction("entry-test-continue", "Tiếp tục Journey", "Quay lại Journey để làm test hoặc xem kết quả", "NAVIGATE", "/journey"),
                        quickAction("view-roadmap", "Xem Roadmap", "Mở lộ trình học cá nhân hóa", "NAVIGATE", "/roadmap"),
                        quickAction("explore-courses", "Khám phá Course", "Tìm khóa học phù hợp với lộ trình", "NAVIGATE", "/courses"))
                : List.of(
                        quickAction("entry-test-start", "Start entry test", "Begin Journey assessment flow", "NAVIGATE", "/journey/create"),
                        quickAction("entry-test-continue", "Continue journey", "Return to Journey for test/result", "NAVIGATE", "/journey"),
                        quickAction("view-roadmap", "View roadmap", "Open your personalized roadmap", "NAVIGATE", "/roadmap"),
                        quickAction("explore-courses", "Explore courses", "Find courses aligned to your roadmap", "NAVIGATE", "/courses"));

        List<String> prompts = isVi
                ? List.of(
                        "Hướng dẫn từng bước làm bài test đầu vào, thật cụ thể theo UI.",
                        "Sau khi có kết quả assessment thì tôi nên làm gì tiếp theo?",
                        "Giúp tôi chọn course đầu tiên dựa trên roadmap hiện tại.")
                : List.of(
                        "Guide me through the entry assessment step by step using the UI flow.",
                        "What should I do right after receiving the assessment result?",
                        "Help me pick the first course based on my roadmap.");

        List<MeowlOnboardingContextResponse.QuickAction> learnerActions = new ArrayList<>(actions);
        learnerActions.add(isVi
                ? quickAction(
                        "learner-premium-benefits",
                        "Quyền lợi Premium",
                        "Xem quyền lợi gói premium cho learner",
                        "NAVIGATE",
                        "/premium")
                : quickAction(
                        "learner-premium-benefits",
                        "Premium benefits",
                        "View learner premium benefits",
                        "NAVIGATE",
                        "/premium"));

        List<String> learnerPrompts = new ArrayList<>(prompts);
        learnerPrompts.add(isVi
                ? (learnerPremiumActive
                        ? "Tóm tắt quyền lợi premium hiện tại của tôi và cách tận dụng cho roadmap hoặc journey."
                        : "Giải thích quyền lợi Premium cho learner (roadmap, mentor booking, expert chat) và nên nâng cấp khi nào.")
                : (learnerPremiumActive
                        ? "Summarize my active premium benefits and how to use them for journey/roadmap."
                        : "Explain learner premium benefits (roadmap, mentor booking, expert chat) and when to upgrade."));

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
                .quickActions(learnerActions)
                .suggestedPrompts(learnerPrompts)
                .contextSummary(summary)
                .promptSection(buildPromptSection(MeowlRoleMode.LEARNER, language, summary, nextAction))
                .build();
    }

    private String buildPromptSection(
            MeowlRoleMode role,
            String language,
            Map<String, String> contextSummary,
            String nextBestAction) {
        boolean isVi = "vi".equals(language);
        StringBuilder section = new StringBuilder();

        section.append(isVi ? "=== CHẾ ĐỘ HƯỚNG DẪN THEO VAI TRÒ ===\n" : "=== ROLE-AWARE GUIDANCE MODE ===\n");
        section.append(isVi ? "Vai trò hiện tại: " : "Resolved role: ").append(role.name()).append('\n');
        section.append(isVi
                ? "Bắt buộc phong cách: rõ ràng, thân thiện, chuyên nghiệp; ưu tiên hành động; tránh dài dòng; luôn chốt bước tiếp theo cụ thể.\n"
                : "Required style: clear, friendly, professional; action-first; avoid long explanations; always end with one concrete next step.\n");
        section.append(isVi
                ? "Không bịa tính năng. Nếu thiếu dữ liệu thì nói rõ giới hạn và đưa phương án hành động khả thi.\n"
                : "Do not invent features. If data is missing, state the limit clearly and propose a practical action.\n");

        if (role == MeowlRoleMode.LEARNER) {
            section.append(isVi
                    ? "Với learner: ưu tiên flow test đầu vào. Khi được hỏi onboarding, hãy nêu: test là gì, vì sao nên làm, mất bao lâu, từng bước thao tác UI, kết quả trả về và bước kế tiếp.\n"
                    : "For learners: prioritize entry-test flow. When onboarding is requested, explain: what it is, why it matters, expected time, exact UI steps, returned result, and next action.\n");
        } else if (role == MeowlRoleMode.RECRUITER) {
            section.append(isVi
                    ? "Với recruiter: nói theo ngữ cảnh vận hành tuyển dụng thực tế (pipeline job, applicants, shortlist), không mô tả chung chung.\n"
                    : "For recruiters: speak in practical hiring operations context (job pipeline, applicants, shortlist), not generic feature descriptions.\n");
        } else if (role == MeowlRoleMode.MENTOR) {
            section.append(isVi
                    ? "Với mentor: hướng dẫn theo trình tự vận hành thật (profile -> course -> publish -> availability -> booking).\n"
                    : "For mentors: guide in practical sequence (profile -> course -> publish -> availability -> booking).\n");
        }

        section.append(isVi ? "Ngữ cảnh user hiện tại:\n" : "Current user context:\n");
        if (role == MeowlRoleMode.LEARNER || role == MeowlRoleMode.RECRUITER) {
            section.append(isVi
                    ? "Khi user hỏi về Premium, chỉ nêu đúng quyền lợi đang có trong hệ thống và giải thích ngắn gọn theo use-case thực tế.\n"
                    : "When users ask about Premium, mention only existing in-system benefits and explain briefly with practical use cases.\n");
        }
        contextSummary.forEach((k, v) -> section.append("- ").append(k).append(": ").append(v).append('\n'));
        section.append(isVi ? "Bước tiếp theo:\n- " : "Next best action:\n- ").append(nextBestAction).append('\n');
        return section.toString();
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
