package com.exe.skillverse_backend.ai_service.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Centralized validator for AI inputs (chatbot and roadmap)
 * - Profanity/abuse filter
 * - Domain constraints (e.g., IELTS score <= 9.0)
 * - Basic sanity checks
 */
@Service
public class InputValidationService {

    private static final List<String> PROFANITY_WORDS = List.of(
            "dm", "ditme", "dmm", "fuck", "fuckyou", "cmm", "cc", "vl", "cl", "địt", "đcm", "fuck you");

    private static final Pattern IELTS_PATTERN = Pattern
            .compile("(?i)ielts\\s*(?:score|band)?\\s*([0-9]+(?:\\.[0-9])?)");

    private static final List<String> LEARNING_KEYWORDS = List.of(
            // Vietnamese
            "hoc", "học", "trở thành", "lap trinh", "lập trình", "tieng anh", "tiếng anh", "ielts", "toeic",
            "python", "java", "spring", "frontend", "backend", "data", "khoa hoc", "khoa học", "machine",
            "ai", "react", "node", "docker", "devops",
            // English
            "learn", "study", "become", "improve", "practice", "english", "roadmap");

    public void validateTextOrThrow(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung không được để trống.");
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        for (String w : PROFANITY_WORDS) {
            if (normalized.contains(w)) {
                throw new IllegalArgumentException("Nội dung chứa từ ngữ không phù hợp. Vui lòng nhập lại lịch sự.");
            }
        }

        // IELTS validation: reject impossible scores (> 9.0)
        var m = IELTS_PATTERN.matcher(normalized);
        while (m.find()) {
            try {
                double score = Double.parseDouble(m.group(1));
                if (score > 9.0) {
                    throw new IllegalArgumentException("Điểm IELTS tối đa là 9.0. Vui lòng nhập mục tiêu hợp lệ.");
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * Validate that a text looks like a learning goal (not random/abusive noise)
     */
    public void validateLearningGoalOrThrow(String goal) {
        validateTextOrThrow(goal);

        String normalized = goal.toLowerCase(Locale.ROOT).trim();

        // Must contain at least 5 alphabetic characters
        long letters = normalized.chars().filter(Character::isLetter).count();
        if (letters < 5) {
            throw new IllegalArgumentException("Mục tiêu học tập quá ngắn/không rõ ràng. Vui lòng mô tả cụ thể hơn.");
        }

        // Heuristic: must contain at least one learning-related keyword
        boolean hasKeyword = LEARNING_KEYWORDS.stream().anyMatch(normalized::contains);
        if (!hasKeyword) {
            throw new IllegalArgumentException(
                    "Mục tiêu không giống mục tiêu học tập. Ví dụ: 'Học Spring Boot 3', 'Trở thành Frontend Developer', 'IELTS 7.0 trong 3 tháng'.");
        }
    }
}
