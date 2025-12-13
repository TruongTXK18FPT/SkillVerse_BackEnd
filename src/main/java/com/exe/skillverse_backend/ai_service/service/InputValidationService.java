package com.exe.skillverse_backend.ai_service.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import org.springframework.stereotype.Service;

/**
 * Enhanced validator for AI inputs with soft warning system
 * - Profanity/abuse filter
 * - Comprehensive domain constraints (IELTS, TOEIC, TOEFL, etc.)
 * - Deprecated technology detection
 * - Time feasibility checks
 * - Returns ValidationResult list with severity levels (INFO/WARNING/ERROR)
 */
@Service
public class InputValidationService {

    private static final List<String> PROFANITY_WORDS = List.of(
            "dm", "ditme", "dmm", "fuck", "fuckyou", "cmm", "cc", "vl", "cl", "địt", "đcm", "fuck you");

    private static final Pattern IELTS_PATTERN = Pattern
            .compile("(?i)ielts\\s*(?:score|band)?\\s*([0-9]+(?:\\.[0-9])?)");

    private static final Pattern TOEIC_PATTERN = Pattern
            .compile("(?i)toeic\\s*(?:score)?\\s*([0-9]+)");

    private static final Pattern TOEFL_PATTERN = Pattern
            .compile("(?i)toefl\\s*(?:ibt)?\\s*(?:score)?\\s*([0-9]+)");

    private static final List<String> LEARNING_KEYWORDS = List.of(
            // Vietnamese
            "hoc", "học", "trở thành", "lap trinh", "lập trình", "tieng anh", "tiếng anh", "ielts", "toeic",
            "python", "java", "spring", "frontend", "backend", "data", "khoa hoc", "khoa học", "machine",
            "ai", "react", "node", "docker", "devops", "muốn", "muon", "làm sao", "lam sao", "cần", "can",
            // English
            "learn", "study", "become", "improve", "practice", "english", "roadmap", "want", "how to", "need");

    // Deprecated technologies map
    private static final Map<String, DeprecatedTech> DEPRECATED_TECHNOLOGIES = Map.ofEntries(
            Map.entry("flash", new DeprecatedTech("Adobe Flash (ngừng hỗ trợ 2020)",
                    List.of("HTML5 Canvas", "WebGL", "Three.js"))),
            Map.entry("angularjs", new DeprecatedTech("AngularJS 1.x (lỗi thời)",
                    List.of("Angular (2+)", "React", "Vue.js"))),
            Map.entry("angular 1", new DeprecatedTech("AngularJS 1.x (lỗi thời)",
                    List.of("Angular (2+)", "React", "Vue.js"))),
            Map.entry("silverlight", new DeprecatedTech("Microsoft Silverlight (ngừng hỗ trợ)",
                    List.of("HTML5", ".NET MAUI", "Blazor"))),
            Map.entry("coffeescript", new DeprecatedTech("CoffeeScript (ít phổ biến)",
                    List.of("TypeScript", "Modern JavaScript ES6+"))),
            Map.entry("bower", new DeprecatedTech("Bower (deprecated)",
                    List.of("npm", "yarn", "pnpm"))));

    // Old versions warning
    private static final Map<String, String> OLD_VERSIONS = Map.of(
            "python 2", "Python 2 đã kết thúc hỗ trợ năm 2020. Khuyến nghị học Python 3.x",
            "php 5", "PHP 5 không còn được hỗ trợ. Khuyến nghị học PHP 7.x hoặc 8.x",
            "node 10", "Node.js 10 đã end-of-life. Khuyến nghị học Node.js 18 LTS hoặc 20 LTS");

    /**
     * Comprehensive validation with soft warnings
     * Returns list of validation results (INFO/WARNING/ERROR)
     */
    public List<ValidationResult> validateWithWarnings(GenerateRoadmapRequest request) {
        List<ValidationResult> results = new ArrayList<>();

        // Validate goal
        results.addAll(validateGoal(request.getGoal()));

        // Check for deprecated technologies
        results.addAll(checkDeprecatedTechnologies(request.getGoal()));

        // Check time feasibility
        results.addAll(checkTimeFeasibility(request.getGoal(), request.getDuration(), request.getExperience()));

        // Check experience vs goal mismatch
        results.addAll(checkExperienceGoalMismatch(request.getGoal(), request.getExperience()));

        if (request.getRoadmapType() != null) {
            String t = request.getRoadmapType().trim().toLowerCase(Locale.ROOT);
            if (!t.equals("skill") && !t.equals("career")) {
                results.add(ValidationResult.warning("roadmapType",
                        "Loại roadmap không hợp lệ. Chỉ hỗ trợ 'skill' hoặc 'career'",
                        "Vui lòng chọn 'skill' (học kỹ năng) hoặc 'career' (lộ trình nghề nghiệp)"));
            }
        }

        if (request.getTarget() == null || request.getTarget().trim().isEmpty()) {
            results.add(ValidationResult.info("target",
                    "Thiếu mục tiêu cụ thể (target)",
                    "Ví dụ: ReactJS, Frontend Developer, Digital Marketing"));
        }

        if (request.getCurrentLevel() != null) {
            String lvl = request.getCurrentLevel().trim().toLowerCase(Locale.ROOT);
            List<String> allowed = List.of("zero", "basic", "intermediate");
            if (!allowed.contains(lvl)) {
                results.add(ValidationResult.warning("currentLevel",
                        "Cấp độ hiện tại không hợp lệ",
                        "Hỗ trợ: zero | basic | intermediate"));
            }
        }

        if (request.getDesiredDuration() != null && request.getDailyTime() != null) {
            String daily = request.getDailyTime().toLowerCase(Locale.ROOT);
            boolean tinyDaily = daily.contains("30") || daily.contains("30 phút");
            if (tinyDaily && request.getDesiredDuration().contains("1 tuần")) {
                results.add(ValidationResult.warning("desiredDuration",
                        "Thời lượng mong muốn quá ngắn so với thời gian học mỗi ngày",
                        "Khuyến nghị tăng thời lượng hoặc tăng thời gian học mỗi ngày"));
            }
        }

        GenerateRoadmapRequest.RoadmapMode mode = request.getRoadmapMode();
        if (mode == GenerateRoadmapRequest.RoadmapMode.SKILL_BASED) {
            if (request.getSkillName() == null || request.getSkillName().trim().isEmpty()) {
                results.add(ValidationResult.error("skillName",
                        "Thiếu tên kỹ năng cho chế độ Skill-based",
                        "Ví dụ: ReactJS, SQL, Figma"));
            }
            if (request.getTargetRole() != null && !request.getTargetRole().isBlank()) {
                results.add(ValidationResult.warning("targetRole",
                        "Không dùng chung field giữa Skill-based và Career-based",
                        "Vui lòng chỉ nhập 'skillName' cho chế độ Skill-based"));
            }
            if (request.getCurrentSkillLevel() != null) {
                String lvl = request.getCurrentSkillLevel().trim().toLowerCase(Locale.ROOT);
                List<String> allowed = List.of("zero", "basic", "intermediate");
                if (!allowed.contains(lvl)) {
                    results.add(ValidationResult.warning("currentSkillLevel",
                            "Cấp độ kỹ năng không hợp lệ",
                            "Hỗ trợ: ZERO | BASIC | INTERMEDIATE"));
                }
            }
        } else if (mode == GenerateRoadmapRequest.RoadmapMode.CAREER_BASED) {
            if (request.getTargetRole() == null || request.getTargetRole().trim().isEmpty()) {
                results.add(ValidationResult.error("targetRole",
                        "Thiếu vai trò nghề nghiệp cho chế độ Career-based",
                        "Ví dụ: Frontend Developer, Digital Marketer"));
            }
            if (request.getSkillName() != null && !request.getSkillName().isBlank()) {
                results.add(ValidationResult.warning("skillName",
                        "Không dùng chung field giữa Skill-based và Career-based",
                        "Vui lòng chỉ nhập 'targetRole' cho chế độ Career-based"));
            }
        }

        return results;
    }

    public List<com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion> generateClarificationQuestions(GenerateRoadmapRequest request) {
        List<com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion> qs = new ArrayList<>();

        if (request.getRoadmapMode() == null) {
            qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                    .field("roadmapMode")
                    .question("Bạn chọn chế độ nào: Học kỹ năng (SKILL_BASED) hay Lộ trình nghề nghiệp (CAREER_BASED)?")
                    .examples(List.of("SKILL_BASED", "CAREER_BASED"))
                    .required(true)
                    .build());
        }

        if (request.getRoadmapMode() == GenerateRoadmapRequest.RoadmapMode.SKILL_BASED) {
            if (request.getSkillName() == null || request.getSkillName().isBlank()) {
                qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                        .field("skillName")
                        .question("Bạn muốn học kỹ năng nào?")
                        .examples(List.of("ReactJS", "SQL", "Figma"))
                        .required(true)
                        .build());
            }
        } else if (request.getRoadmapMode() == GenerateRoadmapRequest.RoadmapMode.CAREER_BASED) {
            if (request.getTargetRole() == null || request.getTargetRole().isBlank()) {
                qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                        .field("targetRole")
                        .question("Bạn muốn hướng đến vai trò nghề nghiệp nào?")
                        .examples(List.of("Frontend Developer", "Digital Marketer", "UI Designer"))
                        .required(true)
                        .build());
            }
        } else {
            if (request.getTarget() == null || request.getTarget().isBlank()) {
                qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                        .field("target")
                        .question("Mục tiêu cụ thể của bạn là gì (kỹ năng hoặc nghề)?")
                        .examples(List.of("ReactJS", "Frontend Developer", "Digital Marketing", "UI Designer"))
                        .required(true)
                        .build());
            }
        }

        if (request.getDailyTime() == null || request.getDailyTime().isBlank()) {
            qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                    .field("dailyTime")
                    .question("Bạn có thể dành bao nhiêu thời gian mỗi ngày?")
                    .examples(List.of("30 phút", "1 giờ", "2 giờ"))
                    .required(false)
                    .build());
        }

        if (request.getLearningStyle() == null && (request.getStyle() == null || request.getStyle().isBlank())) {
            qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                    .field("learningStyle")
                    .question("Phong cách học ưa thích của bạn?")
                    .examples(List.of("Theo dự án", "Lý thuyết trước", "Video", "Thực hành"))
                    .required(false)
                    .build());
        }

        if (request.getPriority() == null || request.getPriority().isBlank()) {
            qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                    .field("priority")
                    .question("Ưu tiên của bạn là nhanh đi làm hay học sâu?")
                    .examples(List.of("Nhanh đi làm", "Học sâu"))
                    .required(false)
                    .build());
        }

        if (request.getIncomeGoal() == null) {
            qs.add(com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion.builder()
                    .field("incomeGoal")
                    .question("Bạn có mục tiêu thu nhập liên quan lộ trình này không?")
                    .examples(List.of("true", "false"))
                    .required(false)
                    .build());
        }

        return qs;
    }

    /**
     * Validate goal field
     */
    private List<ValidationResult> validateGoal(String goal) {
        List<ValidationResult> results = new ArrayList<>();

        if (goal == null || goal.trim().isEmpty()) {
            results.add(ValidationResult.error("goal",
                    "Mục tiêu không được để trống",
                    "Vui lòng nhập mục tiêu học tập cụ thể"));
            return results;
        }

        String normalized = goal.toLowerCase(Locale.ROOT).trim();

        // Check profanity
        for (String word : PROFANITY_WORDS) {
            if (normalized.contains(word)) {
                results.add(ValidationResult.error("goal",
                        "Nội dung chứa từ ngữ không phù hợp",
                        "Vui lòng nhập lại mục tiêu một cách lịch sự"));
                return results;
            }
        }

        // Check test scores
        results.addAll(validateTestScores(normalized));

        // Check if looks like learning goal
        long letters = normalized.chars().filter(Character::isLetter).count();
        if (letters < 5) {
            results.add(ValidationResult.error("goal",
                    "Mục tiêu quá ngắn hoặc không rõ ràng",
                    "Vui lòng mô tả cụ thể hơn (ít nhất 5 chữ cái). Ví dụ: 'Học Spring Boot', 'Trở thành Data Analyst'"));
            return results;
        }

        boolean hasKeyword = LEARNING_KEYWORDS.stream().anyMatch(normalized::contains);
        if (!hasKeyword) {
            results.add(ValidationResult.warning("goal",
                    "Mục tiêu có vẻ không liên quan đến học tập",
                    "Để AI tạo lộ trình tốt hơn, hãy dùng từ khóa như: 'học', 'muốn', 'trở thành'... Ví dụ: 'Học Python cơ bản'"));
        }

        // Check if goal is too vague
        List<String> vagueTerms = List.of("học", "code", "lập trình", "thiết kế", "marketing");
        if (vagueTerms.stream().anyMatch(term -> normalized.equals(term) || normalized.equals(term + " "))) {
            results.add(ValidationResult.info("goal",
                    "Mục tiêu khá chung chung",
                    "Để có lộ trình chi tiết hơn, hãy cụ thể hóa: 'Học Python cơ bản', 'Học thiết kế đồ họa với Canva'"));
        }

        return results;
    }

    /**
     * Validate test scores (IELTS, TOEIC, TOEFL)
     */
    private List<ValidationResult> validateTestScores(String text) {
        List<ValidationResult> results = new ArrayList<>();

        // IELTS validation (max 9.0)
        Matcher ielts = IELTS_PATTERN.matcher(text);
        while (ielts.find()) {
            try {
                double score = Double.parseDouble(ielts.group(1));
                if (score > 9.0) {
                    results.add(ValidationResult.error("goal",
                            "Điểm IELTS tối đa là 9.0",
                            "Vui lòng điều chỉnh mục tiêu về 'IELTS 9.0' hoặc thấp hơn"));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // TOEIC validation (max 990)
        Matcher toeic = TOEIC_PATTERN.matcher(text);
        while (toeic.find()) {
            try {
                int score = Integer.parseInt(toeic.group(1));
                if (score > 990) {
                    results.add(ValidationResult.error("goal",
                            "Điểm TOEIC tối đa là 990",
                            "Vui lòng điều chỉnh mục tiêu về 'TOEIC 990' hoặc thấp hơn"));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // TOEFL validation (max 120)
        Matcher toefl = TOEFL_PATTERN.matcher(text);
        while (toefl.find()) {
            try {
                int score = Integer.parseInt(toefl.group(1));
                if (score > 120) {
                    results.add(ValidationResult.error("goal",
                            "Điểm TOEFL iBT tối đa là 120",
                            "Vui lòng điều chỉnh mục tiêu về 'TOEFL 120' hoặc thấp hơn"));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return results;
    }

    /**
     * Check for deprecated technologies
     */
    private List<ValidationResult> checkDeprecatedTechnologies(String goal) {
        List<ValidationResult> results = new ArrayList<>();
        String normalized = goal.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, DeprecatedTech> entry : DEPRECATED_TECHNOLOGIES.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                DeprecatedTech tech = entry.getValue();
                results.add(ValidationResult.warning("goal",
                        tech.message + " - Không còn được khuyến khích",
                        "Thay thế bằng: " + String.join(", ", tech.alternatives)));
            }
        }

        for (Map.Entry<String, String> entry : OLD_VERSIONS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                results.add(ValidationResult.warning("goal",
                        entry.getValue(),
                        "Khuyến nghị học phiên bản mới hơn để có cơ hội việc làm tốt hơn"));
            }
        }

        return results;
    }

    /**
     * Check time feasibility
     */
    private List<ValidationResult> checkTimeFeasibility(String goal, String duration, String experience) {
        List<ValidationResult> results = new ArrayList<>();
        String normalizedGoal = goal.toLowerCase();

        // Complex goals with short duration
        List<String> complexKeywords = List.of("master", "chuyên sâu", "architecture", "system design", "full stack",
                "full-stack");
        boolean isComplex = complexKeywords.stream().anyMatch(normalizedGoal::contains);

        List<String> shortDurations = List.of("2 tuần", "3 tuần", "2 weeks", "3 weeks");
        boolean isShortDuration = shortDurations.stream().anyMatch(duration::contains);

        if (isComplex && isShortDuration) {
            results.add(ValidationResult.warning("duration",
                    "Mục tiêu phức tạp với thời gian ngắn",
                    "Để 'master' hoặc học 'chuyên sâu', khuyến nghị thời gian tối thiểu 3-6 tháng. Trong " + duration
                            + " bạn sẽ học được nền tảng cơ bản"));
        }

        return results;
    }

    /**
     * Check experience vs goal mismatch
     */
    private List<ValidationResult> checkExperienceGoalMismatch(String goal, String experience) {
        List<ValidationResult> results = new ArrayList<>();
        String normalizedGoal = goal.toLowerCase();

        boolean isAdvancedExperience = experience != null &&
                (experience.toLowerCase().contains("nâng cao") || experience.toLowerCase().contains("advanced"));
        boolean isBeginnerExperience = experience != null &&
                (experience.toLowerCase().contains("mới bắt đầu") || experience.toLowerCase().contains("beginner"));

        // Advanced user with basic goal
        boolean isBasicGoal = normalizedGoal.contains("cơ bản") || normalizedGoal.contains("basic") ||
                normalizedGoal.contains("beginner") || normalizedGoal.contains("nhập môn");

        if (isAdvancedExperience && isBasicGoal) {
            results.add(ValidationResult.info("goal",
                    "Bạn đã có trình độ nâng cao nhưng mục tiêu là 'cơ bản'",
                    "Lộ trình sẽ tập trung vào best practices và advanced topics thay vì nội dung cơ bản"));
        }

        // Beginner with advanced goal
        boolean isAdvancedGoal = normalizedGoal.contains("master") || normalizedGoal.contains("nâng cao") ||
                normalizedGoal.contains("advanced") || normalizedGoal.contains("chuyên sâu");

        if (isBeginnerExperience && isAdvancedGoal) {
            results.add(ValidationResult.info("goal",
                    "Mục tiêu nâng cao cho người mới bắt đầu",
                    "Lộ trình sẽ bao gồm phần nền tảng chi tiết trước khi vào nội dung nâng cao"));
        }

        return results;
    }

    /**
     * Legacy method - throws on error (for backward compatibility)
     */
    public void validateTextOrThrow(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung không được để trống.");
        }
        // Basic profanity check only
        String normalized = input.toLowerCase(Locale.ROOT);
        for (String w : PROFANITY_WORDS) {
            if (normalized.contains(w)) {
                throw new IllegalArgumentException("Nội dung chứa từ ngữ không phù hợp. Vui lòng nhập lại lịch sự.");
            }
        }
    }

    /**
     * Legacy method - throws on error (for backward compatibility)
     */
    public void validateLearningGoalOrThrow(String goal) {
        List<ValidationResult> results = validateGoal(goal);
        Optional<ValidationResult> firstError = results.stream()
                .filter(ValidationResult::isError)
                .findFirst();

        if (firstError.isPresent()) {
            throw new IllegalArgumentException(firstError.get().getMessage());
        }
    }

    // Helper class
    private static class DeprecatedTech {
        String message;
        List<String> alternatives;

        DeprecatedTech(String message, List<String> alternatives) {
            this.message = message;
            this.alternatives = alternatives;
        }
    }
}
