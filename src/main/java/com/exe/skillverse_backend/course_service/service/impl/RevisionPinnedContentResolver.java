package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RevisionPinnedContentResolver {

    private static final int CONTENT_SNAPSHOT_VERSION_V1 = 1;

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRevisionRepository courseRevisionRepository;

    public Optional<List<ModuleDetailDTO>> resolveModulesWithContent(Course course, Long actorId) {
        if (course == null || actorId == null) {
            return Optional.empty();
        }

        Optional<CourseEnrollment> enrollmentOpt = enrollmentRepository.findByCourseIdAndUserId(course.getId(), actorId);
        if (enrollmentOpt.isEmpty()) {
            return Optional.empty();
        }

        CourseEnrollment enrollment = enrollmentOpt.get();
        if (!hasLearningAccess(enrollment.getStatus())) {
            return Optional.empty();
        }

        Long revisionId = resolveLearningRevisionId(course, enrollment);
        if (revisionId == null) {
            return Optional.empty();
        }

        Optional<CourseRevision> revisionOpt = courseRevisionRepository.findByIdAndCourse_Id(revisionId, course.getId());
        if (revisionOpt.isEmpty()) {
            log.warn(
                    "Pinned revision {} not found for course {} while resolving content for actor {}",
                    revisionId,
                    course.getId(),
                    actorId
            );
            return Optional.empty();
        }

        JsonNode snapshot = revisionOpt.get().getContentSnapshotJson();
        if (snapshot == null || snapshot.isNull()) {
            return Optional.empty();
        }

        Integer snapshotVersion = parseInteger(snapshot.path("snapshotVersion"), CONTENT_SNAPSHOT_VERSION_V1);
        if (snapshotVersion == null || snapshotVersion != CONTENT_SNAPSHOT_VERSION_V1) {
            log.warn(
                    "Unsupported snapshotVersion {} for course {} revision {}",
                    snapshotVersion,
                    course.getId(),
                    revisionId
            );
            return Optional.empty();
        }

        JsonNode modulesNode = snapshot.path("modules");
        if (!modulesNode.isArray()) {
            return Optional.empty();
        }

        List<ModuleDetailDTO> modules = parseModules(course.getId(), revisionId, modulesNode);
        return Optional.of(modules);
    }

    public Optional<List<ModuleSummaryDTO>> resolveModuleSummaries(Course course, Long actorId) {
        return resolveModulesWithContent(course, actorId)
                .map(modules -> modules.stream()
                        .map(module -> new ModuleSummaryDTO(
                                module.getId(),
                                module.getTitle(),
                                module.getDescription(),
                                module.getOrderIndex()
                        ))
                        .toList());
    }

    public boolean hasLearningAccessEnrollment(Course course, Long actorId) {
        if (course == null || course.getId() == null || actorId == null) {
            return false;
        }
        return enrollmentRepository.findByCourseIdAndUserId(course.getId(), actorId)
                .map(CourseEnrollment::getStatus)
                .map(this::hasLearningAccess)
                .orElse(false);
    }

    public Optional<ModuleDetailDTO> resolvePinnedModule(Course course, Long actorId, Long moduleId) {
        if (moduleId == null) {
            return Optional.empty();
        }
        return resolveModulesWithContent(course, actorId)
                .flatMap(modules -> modules.stream()
                        .filter(module -> moduleId.equals(module.getId()))
                        .findFirst());
    }

    public boolean isLessonInPinnedRevision(Course course, Long actorId, Long lessonId) {
        if (lessonId == null) {
            return false;
        }
        return resolveModulesWithContent(course, actorId)
                .map(modules -> modules.stream().anyMatch(module -> module.getLessons() != null
                        && module.getLessons().stream().anyMatch(lesson -> lessonId.equals(lesson.getId()))))
                .orElse(false);
    }

    public boolean isQuizInPinnedRevision(Course course, Long actorId, Long quizId) {
        if (quizId == null) {
            return false;
        }
        return resolveModulesWithContent(course, actorId)
                .map(modules -> modules.stream().anyMatch(module -> module.getQuizzes() != null
                        && module.getQuizzes().stream().anyMatch(quiz -> quizId.equals(quiz.getId()))))
                .orElse(false);
    }

    public boolean isAssignmentInPinnedRevision(Course course, Long actorId, Long assignmentId) {
        if (assignmentId == null) {
            return false;
        }
        return resolveModulesWithContent(course, actorId)
                .map(modules -> modules.stream().anyMatch(module -> module.getAssignments() != null
                        && module.getAssignments().stream().anyMatch(assignment -> assignmentId.equals(assignment.getId()))))
                .orElse(false);
    }

    private List<ModuleDetailDTO> parseModules(Long courseId, Long revisionId, JsonNode modulesNode) {
        List<ModuleDetailDTO> modules = new ArrayList<>();

        for (int moduleIndex = 0; moduleIndex < modulesNode.size(); moduleIndex++) {
            JsonNode moduleNode = modulesNode.get(moduleIndex);
            if (moduleNode == null || moduleNode.isNull()) {
                continue;
            }

            // Use array position as the display order source of truth for pinned snapshots.
            // This prevents legacy / stale orderIndex values from reshuffling learner UI.
            int moduleOrder = moduleIndex;
            Long moduleId = parseLong(moduleNode.path("id"));
            if (!isPositiveEntityId(moduleId)) {
                log.warn(
                        "Skipping module without valid positive id in pinned snapshot (courseId={}, revisionId={}, moduleIndex={})",
                        courseId,
                        revisionId,
                        moduleIndex
                );
                continue;
            }

            List<LessonBriefDTO> lessons = new ArrayList<>();
            List<QuizSummaryDTO> quizzes = new ArrayList<>();
            List<AssignmentSummaryDTO> assignments = new ArrayList<>();

            JsonNode lessonLikeItems = moduleNode.path("lessons");
            if (lessonLikeItems.isArray()) {
                for (int itemIndex = 0; itemIndex < lessonLikeItems.size(); itemIndex++) {
                    JsonNode itemNode = lessonLikeItems.get(itemIndex);
                    if (itemNode == null || itemNode.isNull()) {
                        continue;
                    }

                    String normalizedType = normalizeItemType(itemNode);
                    // Keep learner ordering aligned with snapshot sequence, even if stored orderIndex is stale.
                    int itemOrder = itemIndex;
                    Long itemId = parseLong(itemNode.path("id"));
                    if (!isPositiveEntityId(itemId)) {
                        log.warn(
                                "Skipping {} item without valid positive id in pinned snapshot (courseId={}, revisionId={}, moduleId={}, itemIndex={})",
                                normalizedType,
                                courseId,
                                revisionId,
                                moduleId,
                                itemIndex
                        );
                        continue;
                    }

                    if ("quiz".equals(normalizedType)) {
                        quizzes.add(buildQuizSummary(itemNode, itemId, itemOrder));
                    } else if ("assignment".equals(normalizedType)) {
                        assignments.add(buildAssignmentSummary(itemNode, itemId, itemOrder, moduleId));
                    } else {
                        lessons.add(buildLessonSummary(itemNode, itemId, itemOrder));
                    }
                }
            }

            quizzes.sort(Comparator
                    .comparing(QuizSummaryDTO::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(QuizSummaryDTO::getId, Comparator.nullsLast(Long::compareTo)));
            assignments.sort(Comparator
                    .comparing(AssignmentSummaryDTO::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(AssignmentSummaryDTO::getId, Comparator.nullsLast(Long::compareTo)));
            lessons.sort(Comparator
                    .comparing(LessonBriefDTO::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(LessonBriefDTO::getId, Comparator.nullsLast(Long::compareTo)));

            modules.add(new ModuleDetailDTO(
                    moduleId,
                    textOrDefault(moduleNode.path("title"), "Module " + (moduleIndex + 1)),
                    textOrNull(moduleNode.path("description")),
                    moduleOrder,
                    null,
                    null,
                    lessons,
                    quizzes,
                    assignments
            ));
        }

        modules.sort(Comparator
                .comparing(ModuleDetailDTO::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ModuleDetailDTO::getId, Comparator.nullsLast(Long::compareTo)));

        return modules;
    }

    private LessonBriefDTO buildLessonSummary(JsonNode itemNode, Long lessonId, int orderIndex) {
        Integer durationSec = parseInteger(itemNode.path("durationSec"), null);
        if (durationSec == null) {
            Integer durationMin = parseInteger(itemNode.path("durationMin"), null);
            if (durationMin != null) {
                durationSec = durationMin * 60;
            }
        }

        String videoUrl = textOrNull(itemNode.path("videoUrl"));
        if (videoUrl == null) {
            videoUrl = textOrNull(itemNode.path("youtubeUrl"));
        }

        return new LessonBriefDTO(
                lessonId,
                textOrDefault(itemNode.path("title"), "Bài học"),
                resolveLessonType(itemNode),
                orderIndex,
                durationSec,
                textOrNull(itemNode.path("contentText")),
                textOrNull(itemNode.path("resourceUrl")),
                videoUrl,
                parseLong(itemNode.path("videoMediaId"))
        );
    }

    private QuizSummaryDTO buildQuizSummary(JsonNode itemNode, Long quizId, int orderIndex) {
        Integer questionCount = null;
        JsonNode questionsNode = itemNode.path("questions");
        if (questionsNode.isArray()) {
            questionCount = questionsNode.size();
        }

        return QuizSummaryDTO.builder()
                .id(quizId)
                .title(textOrDefault(itemNode.path("title"), "Quiz"))
                .description(firstNonBlank(
                        textOrNull(itemNode.path("quizDescription")),
                        textOrNull(itemNode.path("description")),
                        textOrNull(itemNode.path("contentText"))
                ))
                .passScore(parseInteger(itemNode.path("passScore"), 80))
                .maxAttempts(parseInteger(itemNode.path("quizMaxAttempts"), null))
                .timeLimitMinutes(parseInteger(itemNode.path("quizTimeLimitMinutes"), null))
                .roundingIncrement(parseInteger(itemNode.path("roundingIncrement"), null))
                .gradingMethod(parseQuizGradingMethod(itemNode.path("gradingMethod")))
                .isAssessment(parseBoolean(itemNode.path("isAssessment")))
                .cooldownHours(parseInteger(itemNode.path("cooldownHours"), null))
                .orderIndex(orderIndex)
                .questionCount(questionCount)
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    private AssignmentSummaryDTO buildAssignmentSummary(
            JsonNode itemNode,
            Long assignmentId,
            int orderIndex,
            Long moduleId
    ) {
        BigDecimal maxScore = parseBigDecimal(itemNode.path("assignmentMaxScore"));
        if (maxScore == null) {
            maxScore = parseBigDecimal(itemNode.path("maxScore"));
        }

        SubmissionType submissionType = parseSubmissionType(itemNode.path("assignmentSubmissionType"));
        if (submissionType == null) {
            submissionType = parseSubmissionType(itemNode.path("submissionType"));
        }

        return new AssignmentSummaryDTO(
                assignmentId,
                textOrDefault(itemNode.path("title"), "Bài tập"),
                firstNonBlank(
                        textOrNull(itemNode.path("assignmentDescription")),
                        textOrNull(itemNode.path("description")),
                        textOrNull(itemNode.path("contentText"))
                ),
                submissionType != null ? submissionType : SubmissionType.TEXT,
                maxScore,
                parseInstant(itemNode.path("dueAt")),
                moduleId,
                orderIndex
        );
    }

    private LessonType resolveLessonType(JsonNode itemNode) {
        String normalizedType = normalizeItemType(itemNode);
        if ("video".equals(normalizedType)) {
            return LessonType.VIDEO;
        }
        if ("codelab".equals(normalizedType)) {
            return LessonType.CODELAB;
        }
        return LessonType.READING;
    }

    private String normalizeItemType(JsonNode itemNode) {
        String raw = textOrNull(itemNode.path("type"));
        if (raw == null) {
            raw = textOrNull(itemNode.path("lessonType"));
        }
        if (raw == null) {
            raw = textOrNull(itemNode.path("itemType"));
        }
        if (raw == null) {
            return "lesson";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "lesson";
        }
        return switch (normalized) {
            case "quiz" -> "quiz";
            case "assignment" -> "assignment";
            case "video" -> "video";
            case "codelab" -> "codelab";
            default -> "lesson";
        };
    }

    private Long resolveLearningRevisionId(Course course, CourseEnrollment enrollment) {
        if (enrollment != null && enrollment.getLearningRevisionId() != null) {
            return enrollment.getLearningRevisionId();
        }
        if (course == null) {
            return null;
        }
        if (course.getActiveRevisionId() != null) {
            return course.getActiveRevisionId();
        }
        return courseRevisionRepository.findTopByCourseIdAndStatusOrderByRevisionNumberDesc(
                course.getId(),
                CourseRevisionStatus.APPROVED
        ).map(CourseRevision::getId).orElse(null);
    }

    private boolean hasLearningAccess(EnrollmentStatus status) {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.COMPLETED;
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.longValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInteger(JsonNode node, Integer defaultValue) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        if (node.canConvertToInt()) {
            return node.intValue();
        }
        if (!node.isTextual()) {
            return defaultValue;
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Instant parseInstant(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private QuizGradingMethod parseQuizGradingMethod(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        try {
            return QuizGradingMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private SubmissionType parseSubmissionType(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        try {
            return SubmissionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Boolean parseBoolean(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.intValue() != 0;
        }
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.asText("").trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return null;
        }
        if ("true".equals(raw) || "1".equals(raw) || "yes".equals(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equals(raw) || "0".equals(raw) || "no".equals(raw)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText("").trim();
            return value.isEmpty() ? null : value;
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return value != null ? value : defaultValue;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isPositiveEntityId(Long id) {
        return id != null && id > 0;
    }
}
