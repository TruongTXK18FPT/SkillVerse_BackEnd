package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
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
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
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
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.planner.mistral.api-key:}")
    private String mistralApiKey;

    @Value("${spring.ai.planner.mistral.model:mistral-small-latest}")
    private String mistralModel;

    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        if (mistralApiKey == null || mistralApiKey.isEmpty()) {
            log.warn("MISTRAL_PLANNER_API_KEY is not set. AI Study Planner will not function correctly.");
            return;
        }

        MistralAiApi mistralAiApi = new MistralAiApi(mistralApiKey);
        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .withModel(mistralModel)
                .withTemperature(0.7)
                .build();
        
        MistralAiChatModel chatModel = new MistralAiChatModel(mistralAiApi, options);
        this.chatClient = ChatClient.builder(chatModel).build();
        log.info("Initialized AI Study Planner with Mistral model: {}", mistralModel);
    }

    private List<StudySessionResponse> parseResponse(String response) {
        // Clean response if it contains markdown code blocks
        if (response.startsWith("```json")) {
            response = response.substring(7);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        
        try {
            return objectMapper.readValue(response, new TypeReference<List<StudySessionResponse>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing AI response", e);
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
            "Bạn là Trợ lý Lập kế hoạch Học tập AI. Tạo lịch học bằng Tiếng Việt cho yêu cầu:\n" +
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
            "QUY TẮC:\n" +
            "1) Mỗi phần tử PHẢI có đủ: title, startTime, endTime, description.\n" +
            "2) startTime và endTime theo ISO 8601 KHÔNG kèm múi giờ (YYYY-MM-DDTHH:mm:ss), hiểu theo múi giờ đã cho.\n" +
            "3) Chỉ tạo phiên trong khung giờ ưu tiên và ngày ưu tiên; tôn trọng thói quen học.\n" +
            "3.1) ƯU TIÊN đặt trong cửa sổ tập trung lý tưởng.\n" +
            "4) Không tạo lịch trong quá khứ; thời điểm phải >= hiện tại và trước hạn chót.\n" +
            "5) Không dùng năm không hợp lệ (ví dụ 2023 nếu hiện tại > 2023).\n" +
            "6) Bảo đảm khoảng nghỉ giữa phiên và giới hạn số phiên/ngày.\n" +
            "7) Tổng thời lượng một ngày không vượt quá giới hạn.\n" +
            (avoidLateNight ? "8) Tránh tạo phiên sau 23:00 hoặc trước 06:00.\n" : allowLateNight ? "8) Nếu tạo phiên sau 23:00 hoặc trước 06:00, thêm cảnh báo \"Học khuya ảnh hưởng sức khỏe\" vào description.\n" : "8) Hạn chế tối đa phiên sau 23:00 hoặc trước 06:00.\n") +
            "7) title gồm số thứ tự phiên + chủ đề; description nêu mục tiêu cụ thể, nội dung chi tiết, gợi ý tài nguyên phù hợp.\n" +
            "8) Chỉ trả về MẢNG JSON thô, không markdown."
            ,
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
    }

    @Override
    public List<StudySessionResponse> generateProposedSchedule(Long userId, GenerateScheduleRequest request) {
        if (chatClient == null) {
            throw new RuntimeException("AI Study Planner service is not correctly initialized (Missing API Key)");
        }
        
        String promptText = getPromptText(request);
        String response = chatClient.prompt()
                .system("Luôn trả lời bằng Tiếng Việt. Tuân thủ múi giờ Việt Nam (Asia/Ho_Chi_Minh). Không trả về markdown.")
                .user(promptText)
                .call()
                .content();
        List<StudySessionResponse> parsed = parseResponse(response);
        ZoneId zone = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone() : "Asia/Ho_Chi_Minh");
        return normalizeSessions(parsed, request.getDurationMinutes(), zone);
    }

    @Override
    public List<StudySessionResponse> refineSchedule(Long userId, RefineScheduleRequest request) {
        if (chatClient == null) {
            throw new RuntimeException("AI Study Planner service is not correctly initialized");
        }

        try {
            String currentScheduleJson = objectMapper.writeValueAsString(request.getCurrentSchedule());
            
            String promptText = String.format(
                "Bạn là một Trợ lý Lập kế hoạch Học tập AI. Tôi có một lịch trình đã tạo, nhưng tôi muốn thay đổi.\n" +
                "Mục tiêu ban đầu: %s\n" +
                "Lịch trình hiện tại (JSON): %s\n" +
                "Phản hồi của người dùng: %s\n\n" +
                "Vui lòng sửa đổi lịch trình dựa trên phản hồi. Chỉ trả về mảng JSON đã cập nhật của các đối tượng (cùng định dạng như trước).\n" +
                "Đảm bảo tiêu đề và mô tả bằng Tiếng Việt.\n" +
                "Không bao gồm bất kỳ định dạng markdown nào.",
                request.getOriginalGoal(),
                currentScheduleJson,
                request.getUserFeedback()
            );

            String response = chatClient.prompt()
                    .system("Luôn trả lời bằng Tiếng Việt. Tuân thủ múi giờ Việt Nam (Asia/Ho_Chi_Minh). Không trả về markdown.")
                    .user(promptText)
                    .call()
                    .content();
            List<StudySessionResponse> parsed = parseResponse(response);
            int inferredDuration = inferDurationMinutes(parsed);
            ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
            return normalizeSessions(parsed, inferredDuration, zone);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing schedule for refinement", e);
        }
    }

    @Override
    @Transactional
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

        List<StudySession> savedSessions = new ArrayList<>();
        for (StudySessionResponse resp : proposed) {
            StudySession session = StudySession.builder()
                    .title(resp.getTitle())
                    .description(resp.getDescription())
                    .startTime(resp.getStartTime())
                    .endTime(resp.getEndTime())
                    .status(StudySessionStatus.SCHEDULED)
                    .user(user)
                    .build();
            savedSessions.add(studySessionRepository.save(session));
        }

        return savedSessions.stream()
                .map(s -> StudySessionResponse.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .description(s.getDescription())
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
