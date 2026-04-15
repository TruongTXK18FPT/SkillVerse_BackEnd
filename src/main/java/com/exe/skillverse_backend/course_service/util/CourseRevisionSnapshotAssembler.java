package com.exe.skillverse_backend.course_service.util;

import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CourseRevisionSnapshotAssembler {

    private CourseRevisionSnapshotAssembler() {
    }

    public static ObjectNode buildCourseContentSnapshot(ObjectMapper objectMapper, Course course, int snapshotVersion) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotVersion", snapshotVersion);
        ObjectNode compatibilityNode = root.putObject("compatibility");
        compatibilityNode.put("autoCompatibleOnly", true);
        compatibilityNode.put("level", "NON_BREAKING");
        ArrayNode modulesNode = root.putArray("modules");

        if (course == null || course.getModules() == null) {
            return root;
        }

        List<Module> modules = new ArrayList<>(course.getModules());
        modules.sort(Comparator
                .comparing(Module::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Module::getId, Comparator.nullsLast(Long::compareTo)));

        for (int moduleIndex = 0; moduleIndex < modules.size(); moduleIndex++) {
            Module module = modules.get(moduleIndex);
            ObjectNode moduleNode = modulesNode.addObject();
            putNullableLong(moduleNode, "id", module.getId());
            moduleNode.put("orderIndex", module.getOrderIndex() != null ? module.getOrderIndex() : moduleIndex);
            putNullableText(moduleNode, "title", module.getTitle());
            putNullableText(moduleNode, "description", module.getDescription());

            ArrayNode lessonLikeItems = moduleNode.putArray("lessons");
            List<ObjectNode> itemNodes = new ArrayList<>();

            List<Lesson> lessons = module.getLessons() == null
                    ? List.of()
                    : new ArrayList<>(module.getLessons());
            lessons.sort(Comparator
                    .comparing(Lesson::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(Lesson::getId, Comparator.nullsLast(Long::compareTo)));
            for (int lessonIndex = 0; lessonIndex < lessons.size(); lessonIndex++) {
                itemNodes.add(buildLessonNode(objectMapper, lessons.get(lessonIndex), lessonIndex));
            }

            List<Quiz> quizzes = module.getQuizzes() == null
                    ? List.of()
                    : new ArrayList<>(module.getQuizzes());
            quizzes.sort(Comparator
                    .comparing(Quiz::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(Quiz::getId, Comparator.nullsLast(Long::compareTo)));
            for (int quizIndex = 0; quizIndex < quizzes.size(); quizIndex++) {
                itemNodes.add(buildQuizNode(objectMapper, quizzes.get(quizIndex), quizIndex));
            }

            List<Assignment> assignments = module.getAssignments() == null
                    ? List.of()
                    : new ArrayList<>(module.getAssignments());
            assignments.sort(Comparator
                    .comparing(Assignment::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(Assignment::getId, Comparator.nullsLast(Long::compareTo)));
            for (int assignmentIndex = 0; assignmentIndex < assignments.size(); assignmentIndex++) {
                itemNodes.add(buildAssignmentNode(objectMapper, assignments.get(assignmentIndex), assignmentIndex));
            }

            itemNodes.sort(Comparator
                    .comparingInt((ObjectNode node) -> node.path("orderIndex").asInt(Integer.MAX_VALUE))
                    .thenComparingInt(node -> itemTypeRank(node.path("type").asText("")))
                    .thenComparingLong(node -> node.path("id").asLong(Long.MAX_VALUE)));

            for (ObjectNode itemNode : itemNodes) {
                lessonLikeItems.add(itemNode);
            }
        }

        return root;
    }

    private static ObjectNode buildLessonNode(ObjectMapper objectMapper, Lesson lesson, int fallbackOrderIndex) {
        ObjectNode node = objectMapper.createObjectNode();
        putNullableLong(node, "id", lesson.getId());
        node.put("orderIndex", lesson.getOrderIndex() != null ? lesson.getOrderIndex() : fallbackOrderIndex);
        node.put(
                "type",
                lesson.getType() != null
                        ? lesson.getType().name().toLowerCase(Locale.ROOT)
                        : "reading"
        );
        putNullableText(node, "title", lesson.getTitle());

        Integer durationSec = lesson.getDurationSec();
        if (durationSec != null) {
            node.put("durationMin", Math.max(0, durationSec / 60));
        } else {
            node.putNull("durationMin");
        }

        putNullableText(node, "contentText", lesson.getContentText());
        putNullableText(node, "resourceUrl", lesson.getResourceUrl());
        putNullableText(node, "youtubeUrl", lesson.getVideoUrl());
        putNullableLong(node, "videoMediaId", lesson.getVideoMedia() != null ? lesson.getVideoMedia().getId() : null);

        ArrayNode attachmentsNode = node.putArray("attachments");
        List<LessonAttachment> attachments = lesson.getAttachments() == null
                ? List.of()
                : new ArrayList<>(lesson.getAttachments());
        attachments.sort(Comparator
                .comparing(LessonAttachment::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(LessonAttachment::getId, Comparator.nullsLast(Long::compareTo)));

        for (int attachmentIndex = 0; attachmentIndex < attachments.size(); attachmentIndex++) {
            LessonAttachment attachment = attachments.get(attachmentIndex);
            ObjectNode attachmentNode = attachmentsNode.addObject();
            putNullableLong(attachmentNode, "id", attachment.getId());
            attachmentNode.put("orderIndex",
                    attachment.getOrderIndex() != null ? attachment.getOrderIndex() : attachmentIndex);
            putNullableText(attachmentNode, "name", attachment.getTitle());
            putNullableLong(attachmentNode, "mediaId",
                    attachment.getMedia() != null ? attachment.getMedia().getId() : null);
            putNullableText(attachmentNode, "url", attachment.getDownloadUrl());
        }

        return node;
    }

    private static ObjectNode buildQuizNode(ObjectMapper objectMapper, Quiz quiz, int fallbackOrderIndex) {
        ObjectNode node = objectMapper.createObjectNode();
        putNullableLong(node, "id", quiz.getId());
        node.put("orderIndex", quiz.getOrderIndex() != null ? quiz.getOrderIndex() : fallbackOrderIndex);
        node.put("type", "quiz");
        putNullableText(node, "title", quiz.getTitle());
        putNullableText(node, "quizDescription", quiz.getDescription());
        putNullableInteger(node, "passScore", quiz.getPassScore());
        putNullableInteger(node, "quizMaxAttempts", quiz.getMaxAttempts());
        putNullableInteger(node, "quizTimeLimitMinutes", quiz.getTimeLimitMinutes());
        putNullableInteger(node, "roundingIncrement", quiz.getRoundingIncrement());
        putNullableText(node, "gradingMethod", quiz.getGradingMethod() != null ? quiz.getGradingMethod().name() : null);
        putNullableInteger(node, "cooldownHours", quiz.getCooldownHours());

        ArrayNode questionsNode = node.putArray("questions");
        List<QuizQuestion> questions = quiz.getQuestions() == null
                ? List.of()
                : new ArrayList<>(quiz.getQuestions());
        questions.sort(Comparator
                .comparing(QuizQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(QuizQuestion::getId, Comparator.nullsLast(Long::compareTo)));

        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            QuizQuestion question = questions.get(questionIndex);
            ObjectNode questionNode = questionsNode.addObject();
            putNullableLong(questionNode, "id", question.getId());
            questionNode.put("orderIndex",
                    question.getOrderIndex() != null ? question.getOrderIndex() : questionIndex);
            putNullableText(questionNode, "text", question.getQuestionText());
            putNullableText(questionNode, "type",
                    question.getQuestionType() != null ? question.getQuestionType().name() : null);
            putNullableInteger(questionNode, "score", question.getScore());

            ArrayNode optionsNode = questionNode.putArray("options");
            List<QuizOption> options = question.getOptions() == null
                    ? List.of()
                    : new ArrayList<>(question.getOptions());
            options.sort(Comparator
                    .comparing(QuizOption::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(QuizOption::getId, Comparator.nullsLast(Long::compareTo)));

            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                QuizOption option = options.get(optionIndex);
                ObjectNode optionNode = optionsNode.addObject();
                putNullableLong(optionNode, "id", option.getId());
                optionNode.put("orderIndex", option.getOrderIndex() != null ? option.getOrderIndex() : optionIndex);
                putNullableText(optionNode, "text", option.getOptionText());
                optionNode.put("correct", Boolean.TRUE.equals(option.getIsCorrect()));
            }
        }

        return node;
    }

    private static ObjectNode buildAssignmentNode(ObjectMapper objectMapper, Assignment assignment, int fallbackOrderIndex) {
        ObjectNode node = objectMapper.createObjectNode();
        putNullableLong(node, "id", assignment.getId());
        node.put("orderIndex", assignment.getOrderIndex() != null ? assignment.getOrderIndex() : fallbackOrderIndex);
        node.put("type", "assignment");
        putNullableText(node, "title", assignment.getTitle());
        putNullableText(node, "assignmentDescription", assignment.getDescription());
        putNullableText(node, "assignmentSubmissionType",
                assignment.getSubmissionType() != null ? assignment.getSubmissionType().name() : null);
        putNullableDecimal(node, "assignmentMaxScore", assignment.getMaxScore());
        putNullableDecimal(node, "assignmentPassingScore", assignment.getPassingScore());
        if (assignment.getIsRequired() != null) {
            node.put("isRequired", assignment.getIsRequired());
        } else {
            node.putNull("isRequired");
        }

        ArrayNode criteriaNode = node.putArray("assignmentCriteria");
        List<AssignmentCriteria> criteria = assignment.getCriteria() == null
                ? List.of()
                : new ArrayList<>(assignment.getCriteria());
        criteria.sort(Comparator
                .comparing(AssignmentCriteria::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AssignmentCriteria::getId, Comparator.nullsLast(Long::compareTo)));

        for (int criteriaIndex = 0; criteriaIndex < criteria.size(); criteriaIndex++) {
            AssignmentCriteria assignmentCriteria = criteria.get(criteriaIndex);
            ObjectNode criteriaItem = criteriaNode.addObject();
            putNullableLong(criteriaItem, "id", assignmentCriteria.getId());
            criteriaItem.put(
                    "orderIndex",
                    assignmentCriteria.getOrderIndex() != null ? assignmentCriteria.getOrderIndex() : criteriaIndex
            );
            putNullableText(criteriaItem, "name", assignmentCriteria.getName());
            putNullableText(criteriaItem, "description", assignmentCriteria.getDescription());
            putNullableDecimal(criteriaItem, "maxPoints", assignmentCriteria.getMaxPoints());
            putNullableDecimal(criteriaItem, "passingPoints", assignmentCriteria.getPassingPoints());
            criteriaItem.put("isRequired", assignmentCriteria.isRequired());
        }

        // AI Grading fields — always write, including false values (absent vs false is ambiguous)
        node.put("aiGradingEnabled",
                Boolean.TRUE.equals(assignment.getAiGradingEnabled()));
        putNullableText(node, "gradingStyle", assignment.getGradingStyle());
        putNullableText(node, "aiGradingPrompt", assignment.getAiGradingPrompt());
        node.put("trustAiEnabled",
                Boolean.TRUE.equals(assignment.getTrustAiEnabled()));

        return node;
    }

    private static int itemTypeRank(String rawType) {
        if (rawType == null) {
            return 0;
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "quiz" -> 1;
            case "assignment" -> 2;
            default -> 0;
        };
    }

    private static void putNullableText(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
            return;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            node.putNull(field);
        } else {
            node.put(field, normalized);
        }
    }

    private static void putNullableLong(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void putNullableInteger(ObjectNode node, String field, Integer value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void putNullableDecimal(ObjectNode node, String field, BigDecimal value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.doubleValue());
        }
    }
}
