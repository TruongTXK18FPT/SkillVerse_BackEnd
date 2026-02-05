package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.study_service.dto.request.RefineScheduleRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiStudySupportServiceImpl implements AiStudySupportService {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
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
    @lombok.Data
    @lombok.Builder
    private static class MistralRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
        @lombok.Data
        @lombok.Builder
        public static class Message {
            private String role;
            private String content;
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MistralResponse {
        private List<Choice> choices;
        @lombok.Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
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
            log.error("Error parsing AI response (standard): {}", response);
            
            try {
                // Configure extremely lenient mapper
                ObjectMapper lenientMapper = new ObjectMapper();
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true);
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true);
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true);
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS.mappedFeature(), true);
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature(), true);
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true);
                lenientMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);
                lenientMapper.findAndRegisterModules();
                
                return lenientMapper.readValue(cleaned, new TypeReference<List<StudySessionResponse>>() {});
            } catch (Exception ex) {
                 log.error("Fallback parsing failed", ex);
                 // Last resort: Try to sanitize backslashes manually if it's the specific error
                 if (ex.getMessage().contains("Unexpected character ('\\'")) {
                     try {
                         String sanitized = cleaned.replace("\\", "\\\\");
                         return objectMapper.readValue(sanitized, new TypeReference<List<StudySessionResponse>>() {});
                     } catch (Exception ex2) {
                         log.error("Double fallback failed", ex2);
                     }
                 }
            }
            
            throw new RuntimeException("Failed to parse AI schedule");
        }
    }

    private String getPromptText(GenerateScheduleRequest request) {
        String tz = request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh";
        String startDateStr = request.getStartDate() != null ? request.getStartDate().toString() : "Hôm nay";
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
        String focusWindows = request.getIdealFocusWindows() != null && !request.getIdealFocusWindows().isEmpty() ? String.join(", ", request.getIdealFocusWindows()) : "Không chỉ định";

        return String.format(
            "Bạn là Trợ lý Lập kế hoạch Học tập AI chuyên nghiệp. Hãy tạo một lịch trình học tập chi tiết, tối ưu bằng Tiếng Việt cho yêu cầu sau:\n" +
            "- Môn học: %s\n" +
            "- Chủ đề trọng tâm: %s\n" +
            "- Thời gian rảnh mô tả: %s\n" +
            "- Ngày bắt đầu: %s\n" +
            "- Hạn chót: %s\n" +
            "- Múi giờ: %s\n" +
            "- Khung giờ ưu tiên: %s\n" +
            "- Ngày ưu tiên: %s\n" +
            "- Thói quen học: %s\n" +
            "- Giới hạn thời gian: %s - %s\n" +
            "- Cửa sổ tập trung lý tưởng: %s\n" +
            "- Thời lượng mỗi phiên: %d phút\n" +
            "- Nghỉ giữa các phiên: %s phút\n" +
            "- Tối đa số phiên/ngày: %s\n" +
            "- Tối đa thời lượng học/ngày: %s phút\n" +
            "- Mức độ: %s\n" +
            "- Phương pháp học: %s\n" +
            "- Mục tiêu mong muốn: %s\n" +
            "- Tài nguyên ưa thích: %s\n\n" +
            "QUY TẮC QUAN TRỌNG:\n" +
            "1) Trả về kết quả là một MẢNG JSON hợp lệ.\n" +
            "2) Mỗi phần tử trong mảng là một object có các trường: title, startTime, endTime, description.\n" +
            "3) Định dạng thời gian startTime và endTime là ISO 8601 (YYYY-MM-DDTHH:mm:ss).\n" +
            "4) Không được chứa bất kỳ văn bản nào khác ngoài chuỗi JSON. Không dùng markdown ```json ... ``` bao quanh kết quả.\n" +
            "5) Nếu không thể tạo lịch, trả về mảng rỗng [].\n" +
            "6) description phải CỰC KỲ CHI TIẾT, sử dụng Markdown (**in đậm**, *nghiêng*, - danh sách) để trình bày mục tiêu và các bước thực hiện cụ thể.\n" +
            "7) Thời gian phải trong tương lai, từ ngày bắt đầu đến hạn chót.\n" +
            "8) Các phiên học nên được phân bổ hợp lý theo phương pháp %s và mức độ %s.\n" +
            (avoidLateNight ? "9) KHÔNG tạo phiên từ 23:00 đến 06:00.\n" : allowLateNight ? "9) Cảnh báo nếu học khuya.\n" : "9) Hạn chế học khuya.\n"),
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
            resources,
            method,
            intensity
        );
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
            throw new RuntimeException("Tính năng AI Study Planner chỉ dành cho gói Premium (Skill-Plus, Student, Mentor-Pro). Vui lòng nâng cấp gói.");
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
        return normalizeSessions(parsed, request.getDurationMinutes(), zone);
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
            ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
            return normalizeSessions(parsed, inferredDuration, zone);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing schedule for refinement", e);
        }
    }

    @Override
    public List<StudySessionResponse> generateSchedule(Long userId, GenerateScheduleRequest request) {
        // Legacy method: generates and saves immediately
        List<StudySessionResponse> proposed = generateProposedSchedule(userId, request);
        ZoneId zone = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        proposed = normalizeSessions(proposed, request.getDurationMinutes(), zone);
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
                return (int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            }
        }
        return 60;
    }

    private List<StudySessionResponse> normalizeSessions(List<StudySessionResponse> sessions, int durationMinutes, ZoneId zone) {
        LocalDateTime nowVn = LocalDateTime.now(zone);
        List<StudySessionResponse> result = new ArrayList<>();
        for (StudySessionResponse s : sessions) {
            LocalDateTime start = s.getStartTime();
            LocalDateTime end = s.getEndTime();
            if (start == null && end == null) {
                start = nowVn;
                end = start.plusMinutes(durationMinutes);
            } else if (start == null) {
                start = end.minusMinutes(durationMinutes);
            } else if (end == null) {
                end = start.plusMinutes(durationMinutes);
            }
            if (start.isBefore(nowVn)) {
                start = nowVn.plusMinutes(5);
                end = start.plusMinutes(durationMinutes);
            }
            int earliestHour = 6;
            int latestHour = 22;
            if (start.getHour() < earliestHour) {
                start = start.withHour(earliestHour).withMinute(0).withSecond(0);
                end = start.plusMinutes(durationMinutes);
            }
            if (end.getHour() >= latestHour || (end.toLocalTime().isAfter(java.time.LocalTime.of(latestHour, 0)))) {
                // shift to next day at earliest
                start = start.plusDays(1).withHour(earliestHour).withMinute(0).withSecond(0);
                end = start.plusMinutes(durationMinutes);
            }
            StudySessionResponse normalized = StudySessionResponse.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .description(s.getDescription())
                    .startTime(start)
                    .endTime(end)
                    .status(s.getStatus())
                    .build();
            result.add(normalized);
        }
        return result;
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
    public com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport checkScheduleHealth(com.exe.skillverse_backend.study_service.dto.request.CheckScheduleHealthRequest request) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        java.time.ZoneId zone = java.time.ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        java.time.LocalTime earliest = request.getEarliestStartLocalTime() != null ? java.time.LocalTime.parse(request.getEarliestStartLocalTime()) : java.time.LocalTime.of(6,0);
        java.time.LocalTime latest = request.getLatestEndLocalTime() != null ? java.time.LocalTime.parse(request.getLatestEndLocalTime()) : java.time.LocalTime.of(22,0);
        java.util.Map<java.time.LocalDate, Integer> dailyMinutes = new java.util.HashMap<>();
        Integer maxDaily = request.getMaxDailyStudyMinutes() != null ? request.getMaxDailyStudyMinutes() : 240;
        Integer minBreak = request.getBreakMinutesBetweenSessions() != null ? request.getBreakMinutesBetweenSessions() : 10;

        java.util.List<StudySessionResponse> sessions = request.getSessions() != null ? request.getSessions() : java.util.List.of();
        sessions.sort(java.util.Comparator.comparing(StudySessionResponse::getStartTime, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        java.util.List<java.time.LocalTime[]> focusWindows = parseFocusWindows(request.getIdealFocusWindows());
        java.util.List<com.exe.skillverse_backend.study_service.dto.response.SessionScore> scores = new java.util.ArrayList<>();

        for (int i = 0; i < sessions.size(); i++) {
            StudySessionResponse s = sessions.get(i);
            if (s.getStartTime() == null || s.getEndTime() == null) {
                warnings.add("Phiên thiếu thời gian bắt đầu/kết thúc: " + (s.getTitle() != null ? s.getTitle() : ("#" + (i+1))));
                continue;
            }
            java.time.LocalTime st = s.getStartTime().toLocalTime();
            java.time.LocalTime et = s.getEndTime().toLocalTime();
            if (st.isBefore(earliest)) {
                errors.add("Phiên bắt đầu trước giờ cho phép ("+earliest+"): " + s.getTitle());
            }
            if (et.isAfter(latest) || et.equals(latest)) {
                errors.add("Phiên kết thúc sau giờ cho phép ("+latest+"): " + s.getTitle());
            }
            int durMin = (int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            java.time.LocalDate day = s.getStartTime().toLocalDate();
            dailyMinutes.put(day, dailyMinutes.getOrDefault(day, 0) + durMin);
            if (dailyMinutes.get(day) > maxDaily) {
                warnings.add("Tổng thời lượng ngày "+day+" vượt quá "+maxDaily+" phút");
            }
            if (i > 0) {
                StudySessionResponse prev = sessions.get(i-1);
                if (prev.getEndTime() != null) {
                    int breakMin = (int) java.time.Duration.between(prev.getEndTime(), s.getStartTime()).toMinutes();
                    if (breakMin < minBreak) {
                        warnings.add("Khoảng nghỉ giữa phiên quá ngắn ("+breakMin+" phút) trước: " + s.getTitle());
                    }
                }
            }
            String pref = request.getStudyPreference();
            if ("morning".equalsIgnoreCase(pref) && st.isAfter(java.time.LocalTime.of(12,0))) {
                warnings.add("Thói quen học buổi sáng nhưng phiên sau 12:00: " + s.getTitle());
            }
            if ("evening".equalsIgnoreCase(pref) && (st.isBefore(java.time.LocalTime.of(17,0)) || et.isAfter(latest))) {
                warnings.add("Thói quen học buổi tối nhưng phiên không ở khung 17:00-22:00: " + s.getTitle());
            }
            if ("night".equalsIgnoreCase(pref)) {
                warnings.add("Học khuya ảnh hưởng sức khỏe: " + s.getTitle());
            }
            int score = computeFocusScore(s, focusWindows);
            scores.add(com.exe.skillverse_backend.study_service.dto.response.SessionScore.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .score(score)
                    .build());
        }

        boolean healthy = errors.isEmpty();
        return com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport.builder()
                .healthy(healthy)
                .warnings(warnings)
                .errors(errors)
                .sessionScores(scores)
                .build();
    }

    @Override
    public com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport suggestHealthyAdjustments(com.exe.skillverse_backend.study_service.dto.request.CheckScheduleHealthRequest request) {
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        java.time.ZoneId zone = java.time.ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        java.time.LocalTime earliest = request.getEarliestStartLocalTime() != null ? java.time.LocalTime.parse(request.getEarliestStartLocalTime()) : java.time.LocalTime.of(6,0);
        java.time.LocalTime latest = request.getLatestEndLocalTime() != null ? java.time.LocalTime.parse(request.getLatestEndLocalTime()) : java.time.LocalTime.of(22,0);
        Integer maxDaily = request.getMaxDailyStudyMinutes() != null ? request.getMaxDailyStudyMinutes() : 240;
        Integer minBreak = request.getBreakMinutesBetweenSessions() != null ? request.getBreakMinutesBetweenSessions() : 10;
        String pref = request.getStudyPreference();

        java.util.List<StudySessionResponse> sessions = request.getSessions() != null ? request.getSessions() : java.util.List.of();
        java.util.Map<java.time.LocalDate, Integer> dailyMinutes = new java.util.HashMap<>();
        java.util.List<StudySessionResponse> adjusted = new java.util.ArrayList<>();
        java.util.List<java.time.LocalTime[]> focusWindows = parseFocusWindows(request.getIdealFocusWindows());
        java.util.List<com.exe.skillverse_backend.study_service.dto.response.SessionScore> scores = new java.util.ArrayList<>();

        for (int i = 0; i < sessions.size(); i++) {
            StudySessionResponse s = sessions.get(i);
            if (s.getStartTime() == null || s.getEndTime() == null) {
                suggestions.add("Thiếu thời gian, đề xuất đặt phiên #" + (i+1) + " vào " + earliest + " với " + minBreak + " phút nghỉ trước.");
                java.time.LocalDateTime start = java.time.LocalDateTime.now(zone).withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                java.time.LocalDateTime end = start.plusMinutes(60);
                adjusted.add(StudySessionResponse.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .description(s.getDescription())
                        .startTime(start)
                        .endTime(end)
                        .status(s.getStatus())
                        .build());
                int score = computeFocusScore(adjusted.get(adjusted.size()-1), focusWindows);
                scores.add(com.exe.skillverse_backend.study_service.dto.response.SessionScore.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .score(score)
                        .build());
                continue;
            }
            java.time.LocalDateTime start = s.getStartTime();
            java.time.LocalDateTime end = s.getEndTime();
            java.time.LocalTime st = start.toLocalTime();
            java.time.LocalTime et = end.toLocalTime();
            boolean moved = false;
            if (st.isBefore(earliest)) {
                suggestions.add("Dời phiên \"" + s.getTitle() + "\" lên " + earliest + " do quá sớm.");
                start = start.withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                end = start.plusMinutes((int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            if (et.isAfter(latest) || et.equals(latest)) {
                suggestions.add("Dời phiên \"" + s.getTitle() + "\" sang ngày kế tiếp lúc " + earliest + " do quá muộn.");
                start = start.plusDays(1).withHour(earliest.getHour()).withMinute(earliest.getMinute()).withSecond(0);
                end = start.plusMinutes((int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            if ("morning".equalsIgnoreCase(pref) && start.toLocalTime().isAfter(java.time.LocalTime.of(12,0))) {
                suggestions.add("Ưu tiên buổi sáng, dời \"" + s.getTitle() + "\" vào khoảng 07:00-10:00.");
                start = start.withHour(7).withMinute(0).withSecond(0);
                end = start.plusMinutes((int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            if ("evening".equalsIgnoreCase(pref) && (start.toLocalTime().isBefore(java.time.LocalTime.of(17,0)) || end.toLocalTime().isAfter(java.time.LocalTime.of(22,0)))) {
                suggestions.add("Ưu tiên buổi tối, dời \"" + s.getTitle() + "\" vào khoảng 19:00-21:00.");
                start = start.withHour(19).withMinute(0).withSecond(0);
                end = start.plusMinutes((int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
                moved = true;
            }
            java.time.LocalDate day = start.toLocalDate();
            int durMin = (int) java.time.Duration.between(start, end).toMinutes();
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
                int breakMin = (int) java.time.Duration.between(prev.getEndTime(), start).toMinutes();
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
            scores.add(com.exe.skillverse_backend.study_service.dto.response.SessionScore.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .score(score)
                    .build());
        }

        adjusted.sort(java.util.Comparator.comparingInt((StudySessionResponse s) -> {
            for (com.exe.skillverse_backend.study_service.dto.response.SessionScore sc : scores) {
                if (sc.getTitle() != null && sc.getTitle().equals(s.getTitle())) {
                    return -sc.getScore();
                }
            }
            return 0;
        }));

        boolean healthy = errors.isEmpty();
        return com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport.builder()
                .healthy(healthy)
                .warnings(warnings)
                .errors(errors)
                .suggestions(suggestions)
                .adjustedSessions(adjusted)
                .sessionScores(scores)
                .build();
    }

    private java.util.List<java.time.LocalTime[]> parseFocusWindows(java.util.List<String> windows) {
        java.util.List<java.time.LocalTime[]> result = new java.util.ArrayList<>();
        if (windows == null) return result;
        for (String w : windows) {
            if (w == null || !w.contains("-")) continue;
            String[] parts = w.split("-");
            try {
                java.time.LocalTime s = java.time.LocalTime.parse(parts[0].trim());
                java.time.LocalTime e = java.time.LocalTime.parse(parts[1].trim());
                result.add(new java.time.LocalTime[]{s, e});
            } catch (Exception ignored) {}
        }
        return result;
    }

    private int computeFocusScore(StudySessionResponse session, java.util.List<java.time.LocalTime[]> windows) {
        if (session.getStartTime() == null || session.getEndTime() == null || windows == null || windows.isEmpty()) return 0;
        java.time.LocalTime st = session.getStartTime().toLocalTime();
        java.time.LocalTime et = session.getEndTime().toLocalTime();
        int total = (int) java.time.Duration.between(st, et).toMinutes();
        int inFocus = 0;
        for (java.time.LocalTime[] win : windows) {
            java.time.LocalTime ws = win[0];
            java.time.LocalTime we = win[1];
            java.time.LocalTime overlapStart = st.isAfter(ws) ? st : ws;
            java.time.LocalTime overlapEnd = et.isBefore(we) ? et : we;
            if (overlapEnd.isAfter(overlapStart)) {
                inFocus += (int) java.time.Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        if (total <= 0) return 0;
        int score = (int) Math.round((inFocus * 100.0) / total);
        return Math.max(0, Math.min(100, score));
    }
}
