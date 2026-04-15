package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.study_service.dto.request.CheckScheduleHealthRequest;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.request.RefineScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport;
import com.exe.skillverse_backend.study_service.dto.response.SessionScore;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiStudySupportServiceImpl implements AiStudySupportService {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.planner.mistral.api-key:}")
    private String mistralApiKey;

    @Value("${spring.ai.planner.mistral.model:mistral-small-latest}")
    private String mistralModel;

    private RestClient restClient;
    private static final String MISTRAL_API_URL = "https://api.mistral.ai/v1/chat/completions";

    @PostConstruct
    public void init() {
        if (mistralApiKey == null || mistralApiKey.isEmpty()) {
            log.warn("MISTRAL_PLANNER_API_KEY is not set. AI Study Planner will not function correctly.");
            return;
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(60 * 1000); // 60s connect
        requestFactory.setReadTimeout(3600 * 1000);  // 1 hour read

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(MISTRAL_API_URL)
                .defaultHeader("Authorization", "Bearer " + mistralApiKey)
                .build();
        
        log.info("Initialized AI Study Planner with Mistral model: {} (Direct REST)", mistralModel);
    }

    // Inner DTOs for Mistral API
    @Data
    @Builder
    private static class MistralRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
        @Data
        @Builder
        public static class Message {
            private String role;
            private String content;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MistralResponse {
        private List<Choice> choices;
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Choice {
            private MistralRequest.Message message;
        }
    }

    private List<StudySessionResponse> parseResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks
        if (cleaned.startsWith("```")) {
            int newlineIndex = cleaned.indexOf("\n");
            if (newlineIndex != -1) {
                cleaned = cleaned.substring(newlineIndex + 1);
            } else {
                // Case where ```json is the only line or something similar
                 cleaned = cleaned.substring(3);
            }
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<StudySessionResponse>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Error parsing AI response (standard attempt): {}", e.getMessage());

            // Fallback 1: Try with lenient mapper
            try {
                ObjectMapper lenientMapper = new ObjectMapper();
                lenientMapper.configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true);
                lenientMapper.configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true);
                lenientMapper.configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true);
                lenientMapper.configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS.mappedFeature(), true);
                lenientMapper.configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature(), true);
                lenientMapper.configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true);
                lenientMapper.configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);
                lenientMapper.findAndRegisterModules();

                return lenientMapper.readValue(cleaned, new TypeReference<List<StudySessionResponse>>() {});
            } catch (Exception ex) {
                log.warn("Fallback parsing attempt failed: {}", ex.getMessage());

                // Fallback 2: Sanitize raw control characters in JSON strings
                // JSON spec requires newlines, tabs, carriage returns inside strings to be escaped as \n, \t, \r
                // But Mistral AI sometimes outputs raw control characters in description fields
                try {
                    String sanitized = sanitizeJsonControlCharacters(cleaned);
                    return objectMapper.readValue(sanitized, new TypeReference<List<StudySessionResponse>>() {});
                } catch (Exception ex2) {
                    log.error("All parsing fallbacks failed. Last error: {}", ex2.getMessage());
                    // Log a snippet of the response for debugging
                    String snippet = cleaned.length() > 500 ? cleaned.substring(0, 500) + "..." : cleaned;
                    log.error("AI Response snippet (first 500 chars): {}", snippet);
                }
            }

            throw new RuntimeException("Failed to parse AI schedule");
        }
    }

    /**
     * Sanitizes raw control characters (newline, tab, carriage return, etc.)
     * that Mistral AI sometimes outputs inside JSON string values.
     * These must be escaped as \n, \t, \r inside JSON strings.
     */
    private String sanitizeJsonControlCharacters(String json) {
        StringBuilder result = new StringBuilder(json.length());
        boolean inString = false;
        char[] chars = json.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c == '"' && (i == 0 || chars[i - 1] != '\\')) {
                // Track if we're inside a string (flip on unescaped quote)
                inString = !inString;
                result.append(c);
            } else if (inString) {
                // We're inside a JSON string value - sanitize control characters
                switch (c) {
                    case '\n': result.append("\\n"); break;
                    case '\r': result.append("\\r"); break;
                    case '\t': result.append("\\t"); break;
                    case '\f': result.append("\\f"); break;
                    case '\b': result.append("\\b"); break;
                    // Escape any other control chars (code 0-31 except \t\n\r)
                    default:
                        if (c < 32) {
                            result.append(String.format("\\u%04x", (int) c));
                        } else {
                            result.append(c);
                        }
                        break;
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private String getPromptText(GenerateScheduleRequest request) {
        String tz = request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh";
        ZoneId zone = ZoneId.of(tz);
        // FIX TZ-1: Always send a concrete date string — never "Hôm nay".
        // Mistral AI would otherwise interpret "Hôm nay" in its default (non-VN) timezone,
        // producing incorrect startTimes like 08:00 UTC instead of 15:00 VN.
        String startDateStr = request.getStartDate() != null
                ? request.getStartDate().toString()
                : LocalDate.now(zone).toString();
        String deadlineStr = request.getDeadline() != null ? request.getDeadline().toString() : "Không có";
        String preferredDays = request.getPreferredDays() != null && !request.getPreferredDays().isEmpty() ? String.join(", ", request.getPreferredDays()) : "Không chỉ định";
        String windows = request.getPreferredTimeWindows() != null && !request.getPreferredTimeWindows().isEmpty() ? String.join(", ", request.getPreferredTimeWindows()) : "Không chỉ định";
        String topics = request.getTopics() != null && !request.getTopics().isEmpty() ? String.join(", ", request.getTopics()) : "Không chỉ định";
        String outcome = request.getDesiredOutcome() != null ? request.getDesiredOutcome() : "Không chỉ định";
        String intensity = request.getIntensityLevel() != null ? request.getIntensityLevel() : "medium";
        String method = request.getStudyMethod() != null ? request.getStudyMethod() : "Pomodoro";
        String resources = request.getResourcesPreference() != null ? request.getResourcesPreference() : "Tài liệu sẵn có";
        String breaks = request.getBreakMinutesBetweenSessions() != null ? request.getBreakMinutesBetweenSessions().toString() : "10";
        String maxPerDay = request.getMaxSessionsPerDay() != null ? request.getMaxSessionsPerDay().toString() : "3";
        String studyPref = request.getStudyPreference() != null ? request.getStudyPreference() : "custom";
        String earliest = request.getEarliestStartLocalTime() != null ? request.getEarliestStartLocalTime() : "06:00";
        String latest = request.getLatestEndLocalTime() != null ? request.getLatestEndLocalTime() : "22:00";
        String maxDailyMin = request.getMaxDailyStudyMinutes() != null ? request.getMaxDailyStudyMinutes().toString() : "240";
        boolean avoidLateNight = request.getAvoidLateNight() != null && request.getAvoidLateNight();
        boolean allowLateNight = request.getAllowLateNight() != null && request.getAllowLateNight();
        String focusWindows = request.getIdealFocusWindows() != null && !request.getIdealFocusWindows().isEmpty() ? String.join(", ", request.getIdealFocusWindows()) : "Khong chi dinh";
        // Load course module + lesson content from DB if suggestedModuleIds is set
        String courseModulesContext = buildCourseModulesContext(request.getSuggestedModuleIds());

        // Build base prompt sections
        String baseParts = String.format(
            "Ban la Tro ly Lap ke hoach Hoc tap AI chuyen nghiep. Hay tao mot lich trinh hoc tap chi tiet, toi uu bang Tieng Viet cho yeu cau sau:\n" +
            "- Mon hoc: %s\n" +
            "- Chu de trong tam: %s\n" +
            "- Thoi gian ranh mo ta: %s\n" +
            "- Ngay bat dau: %s\n" +
            "- Han chot: %s\n" +
            "- Mu gio: %s\n" +
            "- Khung gio uu tien: %s\n" +
            "- Ngay uu tien: %s\n" +
            "- Thoi quen hoc: %s\n" +
            "- Gioi han thoi gian: %s - %s\n" +
            "- Cua so tap trung ly tuong: %s\n" +
            "- Thoi luong moi phien: %d phut\n" +
            "- Nghi giua cac phien: %s phut\n" +
            "- Toi da so phien/ngay: %s\n" +
            "- Toi da thoi luong hoc/ngay: %s phut\n" +
            "- Muc do: %s\n" +
            "- Phuong phap hoc: %s\n" +
            "- Muc tieu mong muon: %s\n" +
            "- Tai nguyen ua thich: %s\n",
            request.getSubjectName(),
            topics,
            request.getFreeTimeDescription(),
            startDateStr,
            deadlineStr,
            tz,
            windows,
            preferredDays,
            studyPref,
            earliest,
            latest,
            focusWindows,
            request.getDurationMinutes(),
            breaks,
            maxPerDay,
            maxDailyMin,
            intensity,
            method,
            outcome,
            resources
        );

        StringBuilder fullPrompt = new StringBuilder(baseParts);

        // Append course module context if available (already includes the header)
        if (courseModulesContext != null && !courseModulesContext.isBlank()) {
            fullPrompt.append("\n");
            fullPrompt.append(courseModulesContext);
            fullPrompt.append("\nLUU Y: Cac phien hoc phai TAP TRUNG vao noi dung cu the ben tren.\n");
            fullPrompt.append("Khong duoc tu doan hay bo sung kien thuc khong co trong khoa hoc.\n");
            fullPrompt.append("Moi phien nen gan voi 1-3 bai hoc (lessons) cu the tu danh sach tren.\n\n");
        }

        // Append rules
        fullPrompt.append("QUY TAC QUAN TRONG:\n");
        fullPrompt.append("1) Tra ve ket qua la mot MANG JSON hop le.\n");
        fullPrompt.append("2) Moi phan tu trong mang la mot object co cac truong: title, startTime, endTime, description.\n");
        fullPrompt.append("3) Dinh dang thoi gian startTime va endTime la ISO 8601 (YYYY-MM-DDTHH:mm:ss).\n");
        fullPrompt.append("4) Khong duoc chua bat ky van ban nao khac ngoai chuoi JSON. Khong dung markdown ```json ... ``` bao quanh ket qua.\n");
        fullPrompt.append("5) Neu khong the tao lich, tra ve mang rong [].\n");
        fullPrompt.append("6) description phai CUC KY CHI TIET, su dung Markdown (**in dam**, *nghieng*, - danh sach) de trinh bay muc tieu va cac buoc thuc hien cu the.\n");
        fullPrompt.append("7) Thoi gian phai trong tuong lai, tu ngay bat dau den han chot.\n");
        fullPrompt.append("8) Cac phien hoc nen duoc phan bo hop ly theo phuong phap " + method + " va muc do " + intensity + ".\n");
        if (avoidLateNight) {
            fullPrompt.append("9) KHONG tao phien tu 23:00 den 06:00.\n");
        } else if (allowLateNight) {
            fullPrompt.append("9) Canh bao neu hoc khuya.\n");
        } else {
            fullPrompt.append("9) Han che hoc khuya.\n");
        }

        return fullPrompt.toString();
    }

    // Helper method to determine Mistral model based on user plan
    private String getMistralModelForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserSubscription subscription = userSubscriptionRepository.findCurrentActiveSubscription(user)
                .orElse(null);

        // Check for Free Tier or No Subscription
        if (subscription == null || 
            (subscription.getPlan() != null && subscription.getPlan().getPlanType() == PremiumPlan.PlanType.FREE_TIER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Tính năng AI Study Planner chỉ dành cho gói Premium (Skill-Plus, Student, Mentor-Pro). Vui lòng nâng cấp gói.");
        }

        String modelToUse = "mistral-small-latest"; // Default
        if (subscription.getPlan() != null) {
            String planName = subscription.getPlan().getName().toLowerCase();
            String planType = subscription.getPlan().getPlanType().toString();
            
            // Check by Plan Name OR Plan Type
            if ((planName.contains("mentor") && planName.contains("pro")) || 
                "PREMIUM_PLUS".equals(planType)) {
                modelToUse = "mistral-large-latest";
            }
        }
        return modelToUse;
    }

    @Override
    public List<StudySessionResponse> generateProposedSchedule(Long userId, GenerateScheduleRequest request) {
        if (restClient == null) {
            throw new RuntimeException("AI Study Planner service is not correctly initialized (Missing API Key)");
        }

        String modelToUse = getMistralModelForUser(userId);

        log.info("Generating schedule for user {} using model {}", userId, modelToUse);
        
        String promptText = getPromptText(request);
        String response = "";
        try {
            // Build request
            MistralRequest mistralRequest = MistralRequest.builder()
                    .model(modelToUse)
                    .temperature(0.7)
                    .messages(List.of(
                            MistralRequest.Message.builder()
                                    .role("system")
                                    .content("Bạn là một chuyên gia lập kế hoạch học tập. Chỉ trả về JSON array thô, không markdown.")
                                    .build(),
                            MistralRequest.Message.builder()
                                    .role("user")
                                    .content(promptText)
                                    .build()
                    ))
                    .build();

            // Execute call
            MistralResponse apiResponse = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mistralRequest)
                    .retrieve()
                    .body(MistralResponse.class);

            if (apiResponse != null && apiResponse.getChoices() != null && !apiResponse.getChoices().isEmpty()) {
                response = apiResponse.getChoices().get(0).getMessage().getContent();
            }
        } catch (Exception e) {
            log.error("Error calling AI service", e);
            throw new RuntimeException("Failed to call AI service: " + e.getMessage());
        }

        if (response == null || response.isBlank()) {
             throw new RuntimeException("AI service returned empty response");
        }

        log.debug("AI Raw Response: {}", response);

        List<StudySessionResponse> parsed = parseResponse(response);
        ZoneId zone = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        return normalizeSessions(parsed, request.getDurationMinutes(), zone, request);
    }

    @Override
    public List<StudySessionResponse> refineSchedule(Long userId, RefineScheduleRequest request) {
        if (restClient == null) {
            throw new RuntimeException("AI Study Planner service is not correctly initialized");
        }

        String modelToUse = getMistralModelForUser(userId);
        log.info("Refining schedule for user {} using model {}", userId, modelToUse);

        try {
            String currentScheduleJson = objectMapper.writeValueAsString(request.getCurrentSchedule());
            
            String promptText = String.format(
                "Bạn là một Trợ lý Lập kế hoạch Học tập AI. Tôi có một lịch trình đã tạo, nhưng tôi muốn thay đổi.\n" +
                "Mục tiêu ban đầu: %s\n" +
                "Lịch trình hiện tại (JSON): %s\n" +
                "Phản hồi của người dùng: %s\n\n" +
                "Vui lòng sửa đổi lịch trình dựa trên phản hồi. Chỉ trả về mảng JSON đã cập nhật của các đối tượng (cùng định dạng như trước).\n" +
                "Đảm bảo tiêu đề và mô tả bằng Tiếng Việt.\n" +
                "Trong phần description, hãy sử dụng Markdown (**in đậm**, *nghiêng*, - danh sách) để trình bày rõ ràng.\n" +
                "Không bao gồm bất kỳ định dạng markdown code block (```json) nào bao quanh kết quả.",
                request.getOriginalGoal(),
                currentScheduleJson,
                request.getUserFeedback()
            );

            String response = "";
            // Build request
            MistralRequest mistralRequest = MistralRequest.builder()
                    .model(modelToUse)
                    .temperature(0.7)
                    .messages(List.of(
                            MistralRequest.Message.builder()
                                    .role("system")
                                    .content("Luôn trả lời bằng Tiếng Việt. Tuân thủ múi giờ Việt Nam (Asia/Ho_Chi_Minh). Không trả về markdown.")
                                    .build(),
                            MistralRequest.Message.builder()
                                    .role("user")
                                    .content(promptText)
                                    .build()
                    ))
                    .build();

            // Execute call
            MistralResponse apiResponse = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mistralRequest)
                    .retrieve()
                    .body(MistralResponse.class);

            if (apiResponse != null && apiResponse.getChoices() != null && !apiResponse.getChoices().isEmpty()) {
                response = apiResponse.getChoices().get(0).getMessage().getContent();
            }

            List<StudySessionResponse> parsed = parseResponse(response);
            int inferredDuration = inferDurationMinutes(parsed);

            // For refineSchedule, derive preferences from current sessions to preserve user's timing pattern.
            // Fallback to evening window if no sessions exist.
            GenerateScheduleRequest fakeRequest = new GenerateScheduleRequest();
            fakeRequest.setDurationMinutes(inferredDuration);
            fakeRequest.setStartDate(null);
            fakeRequest.setAllowLateNight(true); // don't clip — user is refining their existing schedule
            fakeRequest.setAvoidLateNight(false);

            if (request.getCurrentSchedule() != null && !request.getCurrentSchedule().isEmpty()) {
                // Derive earliest/latest from actual session times
                LocalTime minStart = request.getCurrentSchedule().stream()
                        .filter(s -> s.getStartTime() != null)
                        .map(s -> s.getStartTime().toLocalTime())
                        .min(LocalTime::compareTo)
                        .orElse(LocalTime.of(18, 30));
                LocalTime maxEnd = request.getCurrentSchedule().stream()
                        .filter(s -> s.getEndTime() != null)
                        .map(s -> s.getEndTime().toLocalTime())
                        .max(LocalTime::compareTo)
                        .orElse(LocalTime.of(22, 0));
                fakeRequest.setEarliestStartLocalTime(minStart.toString());
                fakeRequest.setLatestEndLocalTime(maxEnd.toString());
                fakeRequest.setPreferredTimeWindows(List.of(minStart + "-" + maxEnd));
            } else {
                fakeRequest.setPreferredTimeWindows(List.of("18:30-22:00"));
                fakeRequest.setEarliestStartLocalTime("18:30");
                fakeRequest.setLatestEndLocalTime("22:00");
            }

            ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
            return normalizeSessions(parsed, inferredDuration, zone, fakeRequest);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing schedule for refinement", e);
        }
    }

    @Override
    public List<StudySessionResponse> generateSchedule(Long userId, GenerateScheduleRequest request) {
        // Legacy method: generates and saves immediately
        List<StudySessionResponse> proposed = generateProposedSchedule(userId, request);
        ZoneId zone = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        proposed = normalizeSessions(proposed, request.getDurationMinutes(), zone, request);
        if (Boolean.TRUE.equals(request.getAllowLateNight())
                && !Boolean.TRUE.equals(request.getConfirmLateNight())
                && hasLateNightSessions(proposed, zone)) {
            throw new RuntimeException("Phát hiện phiên học khuya (sau 23:00 hoặc trước 06:00). Vui lòng xác nhận trước khi tạo lịch.");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<StudySession> sessionsToSave = new ArrayList<>();
        for (StudySessionResponse resp : proposed) {
            StudySession session = StudySession.builder()
                    .title(resp.getTitle())
                    .fullDescription(resp.getDescription())
                    .startTime(resp.getStartTime())
                    .endTime(resp.getEndTime())
                    .status(StudySessionStatus.SCHEDULED)
                    .user(user)
                    .build();
            sessionsToSave.add(session);
        }

        List<StudySession> savedSessions = studySessionRepository.saveAll(sessionsToSave);

        return savedSessions.stream()
                .map(s -> StudySessionResponse.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .description(s.getFullDescription())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .status(s.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    private int inferDurationMinutes(List<StudySessionResponse> sessions) {
        for (StudySessionResponse s : sessions) {
            if (s.getStartTime() != null && s.getEndTime() != null) {
                return (int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            }
        }
        return 60;
    }

    private List<StudySessionResponse> normalizeSessions(
            List<StudySessionResponse> sessions,
            int durationMinutes,
            ZoneId zone,
            GenerateScheduleRequest request) {

        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        LocalDateTime nowVn = LocalDateTime.now(zone);
        LocalDate effectiveBaseDate = request.getStartDate() != null
                ? request.getStartDate()
                : nowVn.toLocalDate();
        LocalTime preferredStart = resolvePreferredStartTime(request);

        // Derive bounds from user preferences — respect night-owl / early-bird users
        LocalTime earliestAllowed = resolveEarliestAllowedTime(request);
        LocalTime latestAllowed = resolveLatestAllowedTime(request);

        List<StudySessionResponse> result = new ArrayList<>(sessions.size());
        for (StudySessionResponse s : sessions) {
            if (s == null) {
                continue; // Defensive: skip null items
            }

            LocalDateTime start = s.getStartTime();
            LocalDateTime end = s.getEndTime();

            // Step 1: Resolve null values using duration
            if (start == null && end == null) {
                start = LocalDateTime.of(effectiveBaseDate, preferredStart);
                end = start.plusMinutes(durationMinutes);
            } else if (start == null) {
                start = end.minusMinutes(durationMinutes);
            } else if (end == null) {
                end = start.plusMinutes(durationMinutes);
            }

            // Step 2: Fix wrong date (TZ-3)
            // If AI misunderstood "Hôm nay" and placed sessions on a past date,
            // shift them to the correct baseDate while keeping the time.
            LocalDate startDate = start.toLocalDate();
            if (startDate.isBefore(effectiveBaseDate)) {
                log.warn("AI returned startTime {} before baseDate {}, preserving time on {}",
                        start, effectiveBaseDate, effectiveBaseDate);
                start = LocalDateTime.of(effectiveBaseDate, start.toLocalTime());
                end = start.plusMinutes(durationMinutes);
            }

            // Step 3: Fix timezone confusion (TZ-4)
            // AI sometimes returns hour < 6 because it interpreted "Hôm nay" in UTC.
            // This is not intentional night-owl behavior — correct it.
            if (start.getHour() < 6) {
                log.warn("AI returned hour {} — likely timezone confusion. Replacing with preferred start {}",
                        start.toLocalTime(), preferredStart);
                start = LocalDateTime.of(effectiveBaseDate, preferredStart);
                end = start.plusMinutes(durationMinutes);
            }

            // Step 4: Prevent sessions truly in the past.
            // Keep intended same-day hours when user explicitly requested a startDate,
            // otherwise timezone-fix tests can be overwritten by "now" clipping.
            boolean hasExplicitStartDate = request.getStartDate() != null;
            boolean isPastDate = start.toLocalDate().isBefore(nowVn.toLocalDate());
            boolean shouldClipPastTimeToday = !hasExplicitStartDate && start.isBefore(nowVn);
            if (isPastDate || shouldClipPastTimeToday) {
                log.info("Session {} is in the past, shifting to now + 5 min", start);
                start = nowVn.plusMinutes(5);
                end = start.plusMinutes(durationMinutes);
            }

            // Step 5: Enforce user-defined time bounds (LB-1)
            // Clip only when user explicitly avoids late night.
            // Intentionally-early sessions (hour < 6) are already fixed by TZ-4.
            boolean avoidLateNight = Boolean.TRUE.equals(request.getAvoidLateNight());
            boolean allowLateNight = Boolean.TRUE.equals(request.getAllowLateNight());
            boolean shouldRespectBounds = !allowLateNight && avoidLateNight;

            if (shouldRespectBounds) {
                if (start.toLocalTime().isBefore(earliestAllowed)) {
                    log.info("Start {} before earliest allowed {}, shifting to {}", start, earliestAllowed, earliestAllowed);
                    start = LocalDateTime.of(effectiveBaseDate, earliestAllowed);
                    end = start.plusMinutes(durationMinutes);
                }
                if (end.toLocalTime().isAfter(latestAllowed)) {
                    log.info("End {} after latest allowed {}, shifting start back", end, latestAllowed);
                    start = LocalDateTime.of(effectiveBaseDate, latestAllowed.minusMinutes(durationMinutes));
                    end = start.plusMinutes(durationMinutes);
                }
            }

            result.add(StudySessionResponse.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .description(s.getDescription())
                    .startTime(start)
                    .endTime(end)
                    .status(s.getStatus())
                    .build());
        }
        return result;
    }

    /**
     * Mirrors JourneyServiceImpl.resolvePreferredStartTime().
     * Resolves the preferred start time from GenerateScheduleRequest.
     */
    private LocalTime resolvePreferredStartTime(GenerateScheduleRequest request) {
        if (request.getPreferredTimeWindows() != null) {
            for (String window : request.getPreferredTimeWindows()) {
                LocalTime parsed = parseTimeRangeStart(window);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        if (request.getEarliestStartLocalTime() != null && !request.getEarliestStartLocalTime().isBlank()) {
            try {
                return LocalTime.parse(request.getEarliestStartLocalTime());
            } catch (Exception ex) {
                log.warn("Could not parse earliestStartLocalTime '{}': {}", request.getEarliestStartLocalTime(), ex.getMessage());
            }
        }
        // Default: afternoon/evening window — most users study after work
        String studyPref = request.getStudyPreference() != null ? request.getStudyPreference().toLowerCase(Locale.ROOT) : "";
        return switch (studyPref) {
            case "morning" -> LocalTime.of(7, 0);
            case "afternoon" -> LocalTime.of(13, 30);
            case "night", "evening" -> LocalTime.of(18, 30);
            default -> LocalTime.of(18, 30);
        };
    }

    /**
     * Parses "HH:MM-HH:MM" time range and returns the start LocalTime.
     */
    private LocalTime parseTimeRangeStart(String window) {
        if (window == null || window.isBlank()) {
            return null;
        }
        String[] parts = window.split("-");
        if (parts.length < 1) {
            return null;
        }
        try {
            String timePart = parts[0].trim();
            if (timePart.length() == 4) {
                timePart = "0" + timePart; // "730" -> "0730"
            }
            if (timePart.length() == 5 && timePart.contains(":")) {
                return LocalTime.parse(timePart);
            }
            if (timePart.length() == 4) {
                return LocalTime.of(
                        Integer.parseInt(timePart.substring(0, 2)),
                        Integer.parseInt(timePart.substring(2, 4))
                );
            }
        } catch (Exception ex) {
            log.warn("Could not parse time range '{}': {}", window, ex.getMessage());
        }
        return null;
    }

    /**
     * Resolves the earliest allowed start time from user preferences.
     * Honors earliestStartLocalTime from the UI, falling back to studyPreference defaults.
     */
    private LocalTime resolveEarliestAllowedTime(GenerateScheduleRequest request) {
        if (request.getEarliestStartLocalTime() != null && !request.getEarliestStartLocalTime().isBlank()) {
            try {
                return LocalTime.parse(request.getEarliestStartLocalTime());
            } catch (Exception ex) {
                log.warn("Could not parse earliestStartLocalTime '{}': {}", request.getEarliestStartLocalTime(), ex.getMessage());
            }
        }
        String studyPref = request.getStudyPreference() != null ? request.getStudyPreference().toLowerCase(Locale.ROOT) : "";
        return switch (studyPref) {
            case "morning"  -> LocalTime.of(6, 0);
            case "afternoon" -> LocalTime.of(12, 0);
            case "night", "evening" -> LocalTime.of(17, 0);
            default -> LocalTime.of(6, 0);
        };
    }

    /**
     * Resolves the latest allowed end time from user preferences.
     * Honors latestEndLocalTime from the UI, falling back to studyPreference defaults.
     */
    private LocalTime resolveLatestAllowedTime(GenerateScheduleRequest request) {
        if (request.getLatestEndLocalTime() != null && !request.getLatestEndLocalTime().isBlank()) {
            try {
                return LocalTime.parse(request.getLatestEndLocalTime());
            } catch (Exception ex) {
                log.warn("Could not parse latestEndLocalTime '{}': {}", request.getLatestEndLocalTime(), ex.getMessage());
            }
        }
        String studyPref = request.getStudyPreference() != null ? request.getStudyPreference().toLowerCase(Locale.ROOT) : "";
        return switch (studyPref) {
            case "morning"  -> LocalTime.of(10, 0);
            case "afternoon" -> LocalTime.of(17, 0);
            case "night", "evening" -> LocalTime.of(23, 30);
            default -> LocalTime.of(22, 0);
        };
    }

    private boolean hasLateNightSessions(List<StudySessionResponse> sessions, ZoneId zone) {
        for (StudySessionResponse s : sessions) {
            if (s.getStartTime() != null) {
                int hour = s.getStartTime().getHour();
                if (hour >= 23 || hour < 6) {
                    return true;
                }
            }
            if (s.getEndTime() != null) {
                int hourEnd = s.getEndTime().getHour();
                if (hourEnd >= 23 || hourEnd < 6) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ScheduleHealthReport checkScheduleHealth(CheckScheduleHealthRequest request) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        ZoneId zone = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        LocalTime earliest = request.getEarliestStartLocalTime() != null ? LocalTime.parse(request.getEarliestStartLocalTime()) : LocalTime.of(6,0);
        LocalTime latest = request.getLatestEndLocalTime() != null ? LocalTime.parse(request.getLatestEndLocalTime()) : LocalTime.of(22,0);
        Map<LocalDate, Integer> dailyMinutes = new HashMap<>();
        Integer maxDaily = request.getMaxDailyStudyMinutes() != null ? request.getMaxDailyStudyMinutes() : 240;
        Integer minBreak = request.getBreakMinutesBetweenSessions() != null ? request.getBreakMinutesBetweenSessions() : 10;

        List<StudySessionResponse> sessions = request.getSessions() != null ? request.getSessions() : List.of();
        sessions.sort(Comparator.comparing(StudySessionResponse::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        List<LocalTime[]> focusWindows = parseFocusWindows(request.getIdealFocusWindows());
        List<SessionScore> scores = new ArrayList<>();

        for (int i = 0; i < sessions.size(); i++) {
            StudySessionResponse s = sessions.get(i);
            if (s.getStartTime() == null || s.getEndTime() == null) {
                warnings.add("Phiên thiếu thời gian bắt đầu/kết thúc: " + (s.getTitle() != null ? s.getTitle() : ("#" + (i+1))));
                continue;
            }
            LocalTime st = s.getStartTime().toLocalTime();
            LocalTime et = s.getEndTime().toLocalTime();
            if (st.isBefore(earliest)) {
                errors.add("Phiên bắt đầu trước giờ cho phép ("+earliest+"): " + s.getTitle());
            }
            if (et.isAfter(latest) || et.equals(latest)) {
                errors.add("Phiên kết thúc sau giờ cho phép ("+latest+"): " + s.getTitle());
            }
            int durMin = (int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            LocalDate day = s.getStartTime().toLocalDate();
            dailyMinutes.put(day, dailyMinutes.getOrDefault(day, 0) + durMin);
            if (dailyMinutes.get(day) > maxDaily) {
                warnings.add("Tổng thời lượng ngày "+day+" vượt quá "+maxDaily+" phút");
            }
            if (i > 0) {
                StudySessionResponse prev = sessions.get(i-1);
                if (prev.getEndTime() != null) {
                    int breakMin = (int) Duration.between(prev.getEndTime(), s.getStartTime()).toMinutes();
                    if (breakMin < minBreak) {
                        warnings.add("Khoảng nghỉ giữa phiên quá ngắn ("+breakMin+" phút) trước: " + s.getTitle());
                    }
                }
            }
            String pref = request.getStudyPreference();
            if ("morning".equalsIgnoreCase(pref) && st.isAfter(LocalTime.of(12,0))) {
                warnings.add("Thói quen học buổi sáng nhưng phiên sau 12:00: " + s.getTitle());
            }
            if ("evening".equalsIgnoreCase(pref) && (st.isBefore(LocalTime.of(17,0)) || et.isAfter(latest))) {
                warnings.add("Thói quen học buổi tối nhưng phiên không ở khung 17:00-22:00: " + s.getTitle());
            }
            if ("night".equalsIgnoreCase(pref)) {
                warnings.add("Học khuya ảnh hưởng sức khỏe: " + s.getTitle());
            }
            int score = computeFocusScore(s, focusWindows);
            scores.add(SessionScore.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .score(score)
                    .build());
        }

        boolean healthy = errors.isEmpty();
        return ScheduleHealthReport.builder()
                .healthy(healthy)
                .warnings(warnings)
                .errors(errors)
                .sessionScores(scores)
                .build();
    }

    @Override
    public ScheduleHealthReport suggestHealthyAdjustments(CheckScheduleHealthRequest request) {
        List<String> suggestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        ZoneId zone = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        LocalTime earliest = request.getEarliestStartLocalTime() != null ? LocalTime.parse(request.getEarliestStartLocalTime()) : LocalTime.of(6,0);
        LocalTime latest = request.getLatestEndLocalTime() != null ? LocalTime.parse(request.getLatestEndLocalTime()) : LocalTime.of(22,0);
        Integer maxDaily = request.getMaxDailyStudyMinutes() != null ? request.getMaxDailyStudyMinutes() : 240;
        Integer minBreak = request.getBreakMinutesBetweenSessions() != null ? request.getBreakMinutesBetweenSessions() : 10;
        String pref = request.getStudyPreference();

        List<StudySessionResponse> sessions = request.getSessions() != null ? request.getSessions() : List.of();
        Map<LocalDate, Integer> dailyMinutes = new HashMap<>();
        List<StudySessionResponse> adjusted = new ArrayList<>();
        List<LocalTime[]> focusWindows = parseFocusWindows(request.getIdealFocusWindows());
        List<SessionScore> scores = new ArrayList<>();

        for (int i = 0; i < sessions.size(); i++) {
            StudySessionResponse s = sessions.get(i);
            if (s.getStartTime() == null || s.getEndTime() == null) {
                suggestions.add("Thiếu thời gian, đề xuất đặt phiên #" + (i+1) + " vào " + earliest + " với " + minBreak + " phút nghỉ trước.");
                LocalDateTime start = LocalDateTime.now(zone).withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                LocalDateTime end = start.plusMinutes(60);
                adjusted.add(StudySessionResponse.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .description(s.getDescription())
                        .startTime(start)
                        .endTime(end)
                        .status(s.getStatus())
                        .build());
                int score = computeFocusScore(adjusted.get(adjusted.size()-1), focusWindows);
                scores.add(SessionScore.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .score(score)
                        .build());
                continue;
            }
            LocalDateTime start = s.getStartTime();
            LocalDateTime end = s.getEndTime();
            LocalTime st = start.toLocalTime();
            LocalTime et = end.toLocalTime();
            boolean moved = false;
            if (st.isBefore(earliest)) {
                suggestions.add("Dời phiên \"" + s.getTitle() + "\" lên " + earliest + " do quá sớm.");
                start = start.withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                end = start.plusMinutes((int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            if (et.isAfter(latest) || et.equals(latest)) {
                suggestions.add("Dời phiên \"" + s.getTitle() + "\" sang ngày kế tiếp lúc " + earliest + " do quá muộn.");
                start = start.plusDays(1).withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                end = start.plusMinutes((int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            if ("morning".equalsIgnoreCase(pref) && start.toLocalTime().isAfter(LocalTime.of(12,0))) {
                suggestions.add("Ưu tiên buổi sáng, dời \"" + s.getTitle() + "\" vào khoảng 07:00-10:00.");
                start = start.withHour(7).withMinute(0).withSecond(0);
                end = start.plusMinutes((int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            if ("evening".equalsIgnoreCase(pref) && (start.toLocalTime().isBefore(LocalTime.of(17,0)) || end.toLocalTime().isAfter(LocalTime.of(22,0)))) {
                suggestions.add("Ưu tiên buổi tối, dời \"" + s.getTitle() + "\" vào khoảng 19:00-21:00.");
                start = start.withHour(19).withMinute(0).withSecond(0);
                end = start.plusMinutes((int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            LocalDate day = start.toLocalDate();
            int durMin = (int) Duration.between(start, end).toMinutes();
            int total = dailyMinutes.getOrDefault(day, 0) + durMin;
            if (total > maxDaily) {
                suggestions.add("Tổng thời lượng ngày " + day + " vượt " + maxDaily + " phút, chia phiên \"" + s.getTitle() + "\" sang ngày kế tiếp.");
                start = start.plusDays(1).withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                end = start.plusMinutes(durMin);
                day = start.toLocalDate();
                moved = true;
            }
            dailyMinutes.put(day, dailyMinutes.getOrDefault(day, 0) + durMin);
            if (!adjusted.isEmpty()) {
                StudySessionResponse prev = adjusted.get(adjusted.size() - 1);
                int breakMin = (int) Duration.between(prev.getEndTime(), start).toMinutes();
                if (breakMin < minBreak) {
                    suggestions.add("Tăng nghỉ giữa phiên trước \"" + s.getTitle() + "\" lên tối thiểu " + minBreak + " phút.");
                    start = prev.getEndTime().plusMinutes(minBreak);
                    end = start.plusMinutes(durMin);
                    moved = true;
                }
            }
            adjusted.add(StudySessionResponse.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .description(s.getDescription())
                    .startTime(start)
                    .endTime(end)
                    .status(s.getStatus())
                    .build());
            if (!moved && ("night".equalsIgnoreCase(pref))) {
                warnings.add("Học khuya ảnh hưởng sức khỏe: " + s.getTitle());
            }
            int score = computeFocusScore(adjusted.get(adjusted.size()-1), focusWindows);
            scores.add(SessionScore.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .score(score)
                    .build());
        }

        adjusted.sort(Comparator.comparingInt((StudySessionResponse s) -> {
            for (SessionScore sc : scores) {
                if (sc.getTitle() != null && sc.getTitle().equals(s.getTitle())) {
                    return -sc.getScore();
                }
            }
            return 0;
        }));

        boolean healthy = errors.isEmpty();
        return ScheduleHealthReport.builder()
                .healthy(healthy)
                .warnings(warnings)
                .errors(errors)
                .suggestions(suggestions)
                .adjustedSessions(adjusted)
                .sessionScores(scores)
                .build();
    }

    private List<LocalTime[]> parseFocusWindows(List<String> windows) {
        List<LocalTime[]> result = new ArrayList<>();
        if (windows == null) return result;
        for (String w : windows) {
            if (w == null || !w.contains("-")) continue;
            String[] parts = w.split("-");
            try {
                LocalTime s = LocalTime.parse(parts[0].trim());
                LocalTime e = LocalTime.parse(parts[1].trim());
                result.add(new LocalTime[]{s, e});
            } catch (Exception ignored) {}
        }
        return result;
    }

    private int computeFocusScore(StudySessionResponse session, List<LocalTime[]> windows) {
        if (session.getStartTime() == null || session.getEndTime() == null || windows == null || windows.isEmpty()) return 0;
        LocalTime st = session.getStartTime().toLocalTime();
        LocalTime et = session.getEndTime().toLocalTime();
        int total = (int) Duration.between(st, et).toMinutes();
        int inFocus = 0;
        for (LocalTime[] win : windows) {
            LocalTime ws = win[0];
            LocalTime we = win[1];
            LocalTime overlapStart = st.isAfter(ws) ? st : ws;
            LocalTime overlapEnd = et.isBefore(we) ? et : we;
            if (overlapEnd.isAfter(overlapStart)) {
                inFocus += (int) Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        if (total <= 0) return 0;
        int score = (int) Math.round((inFocus * 100.0) / total);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Load course module + lesson content from suggestedModuleIds and format as a text block.
     *
     * <p>Called by getPromptText() when suggestedModuleIds is set on the request.
     * This lives in AiStudySupportServiceImpl (study_service) rather than JourneyServiceImpl
     * because it belongs to the layer that builds the AI prompt — not the orchestration layer.
     *
     * @param moduleIdStrs list of module IDs (as strings) from the roadmap node
     * @return formatted text block with module titles, lesson titles, durations, content summaries,
     *         or null if no valid module IDs or no modules found
     */
    private String buildCourseModulesContext(List<String> moduleIdStrs) {
        if (moduleIdStrs == null || moduleIdStrs.isEmpty()) {
            return null;
        }

        List<Long> moduleIds = new ArrayList<>();
        for (String idStr : moduleIdStrs) {
            if (idStr == null || idStr.isBlank()) {
                continue;
            }
            try {
                moduleIds.add(Long.parseLong(idStr.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid module IDs
            }
        }

        if (moduleIds.isEmpty()) {
            return null;
        }

        List<Module> modules = moduleRepository.findByIdInWithLessons(moduleIds);
        if (modules.isEmpty()) {
            log.debug("No modules found for suggestedModuleIds={}", moduleIdStrs);
            return null;
        }

        modules.sort(Comparator.comparing(Module::getOrderIndex));

        StringBuilder sb = new StringBuilder();
        sb.append("NOI DUNG KHOA HOC LIEN QUAN:\n");
        sb.append("==================================================\n\n");

        for (Module mod : modules) {
            sb.append("[Module] ").append(mod.getTitle()).append("\n");
            if (mod.getDescription() != null && !mod.getDescription().isBlank()) {
                sb.append("   Mo ta: ").append(safeTruncate(mod.getDescription(), 200)).append("\n");
            }

            List<Lesson> lessons = lessonRepository.findByModuleIdOrderByOrderIndexAsc(mod.getId());
            if (lessons.isEmpty()) {
                sb.append("   (Chua co bai hoc nao)\n");
            } else {
                for (Lesson lesson : lessons) {
                    sb.append("   [Lesson] ").append(lesson.getTitle());
                    if (lesson.getDurationSec() != null && lesson.getDurationSec() > 0) {
                        sb.append(" (").append(lesson.getDurationSec() / 60).append(" phut)");
                    }
                    if (lesson.getContentText() != null && !lesson.getContentText().isBlank()) {
                        sb.append(": ").append(safeTruncate(lesson.getContentText(), 300));
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String safeTruncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
