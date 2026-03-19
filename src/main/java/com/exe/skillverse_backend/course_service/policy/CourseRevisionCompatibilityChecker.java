package com.exe.skillverse_backend.course_service.policy;

import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseRevisionCompatibilityChecker {

    private static final List<String> BREAKING_RULE_POINTERS = List.of(
            "/completionRule",
            "/completionPolicy",
            "/certificateRule",
            "/passRule"
    );

    private static final List<String> QUIZ_BREAKING_RULE_POINTERS = List.of(
            "/passScore",
            "/passingScore",
            "/completionRule",
            "/completionPolicy",
            "/certificateRule",
            "/passRule"
    );

    private static final List<String> ASSIGNMENT_BREAKING_RULE_POINTERS = List.of(
            "/passingScore",
            "/isRequired",
            "/required",
            "/completionRule",
            "/completionPolicy",
            "/certificateRule",
            "/passRule"
    );

    private static final List<String> LESSON_BREAKING_RULE_POINTERS = List.of(
            "/type",
            "/lessonType",
            "/completionRule",
            "/completionPolicy",
            "/certificateRule",
            "/passRule"
    );

    /**
     * A revision is non-breaking when:
     * 1) It is explicitly marked compatible for auto-upgrade.
     * 2) It does not change completion/certificate rules retroactively.
     * 3) It does not change pass/completion conditions on existing items.
     * 4) It does not remove existing learning items from learner-facing content.
     */
    public boolean isNonBreaking(CourseRevision sourceRevision, CourseRevision targetRevision) {
        return evaluateCompatibility(sourceRevision, targetRevision).isNonBreaking();
    }

    public CompatibilityResult evaluateCompatibility(CourseRevision sourceRevision, CourseRevision targetRevision) {
        if (sourceRevision == null || targetRevision == null) {
            return CompatibilityResult.breaking("INVALID_INPUT", "source_or_target_revision_missing");
        }

        JsonNode sourceSnapshot = sourceRevision.getContentSnapshotJson();
        JsonNode targetSnapshot = targetRevision.getContentSnapshotJson();
        if (sourceSnapshot == null || targetSnapshot == null) {
            return CompatibilityResult.breaking("SNAPSHOT_MISSING", "source_or_target_snapshot_missing");
        }

        if (isLegacyEmptySnapshot(sourceSnapshot)) {
            return CompatibilityResult.breaking("LEGACY_EMPTY_SOURCE", "source_snapshot_empty_object");
        }

        if (!isMarkedCompatible(targetSnapshot)) {
            return CompatibilityResult.breaking("TARGET_NOT_MARKED_COMPATIBLE", "compatibility_flag_or_level_missing");
        }

        String courseRuleChange = findBreakingCourseRuleChange(sourceSnapshot, targetSnapshot);
        if (courseRuleChange != null) {
            return CompatibilityResult.breaking("COURSE_RULE_CHANGED", courseRuleChange);
        }

        String itemRuleChange = findBreakingItemRuleChange(sourceSnapshot, targetSnapshot);
        if (itemRuleChange != null) {
            return CompatibilityResult.breaking("ITEM_RULE_CHANGED", itemRuleChange);
        }

        String removedItem = findRemovedLearningItem(sourceSnapshot, targetSnapshot);
        if (removedItem != null) {
            return CompatibilityResult.breaking("ITEM_REMOVED", removedItem);
        }

        return CompatibilityResult.nonBreaking();
    }

    private boolean isMarkedCompatible(JsonNode targetSnapshot) {
        JsonNode autoCompatibleFlag = targetSnapshot.at("/compatibility/autoCompatibleOnly");
        if (autoCompatibleFlag.isBoolean()) {
            return autoCompatibleFlag.booleanValue();
        }

        JsonNode compatibilityLevel = targetSnapshot.at("/compatibility/level");
        if (!compatibilityLevel.isTextual()) {
            return false;
        }

        String level = compatibilityLevel.asText("");
        return "NON_BREAKING".equalsIgnoreCase(level)
                || "COMPATIBLE".equalsIgnoreCase(level)
                || "AUTO_COMPATIBLE_ONLY".equalsIgnoreCase(level);
    }

    private String findBreakingCourseRuleChange(JsonNode sourceSnapshot, JsonNode targetSnapshot) {
        for (String pointer : BREAKING_RULE_POINTERS) {
            JsonNode sourceValue = sourceSnapshot.at(pointer);
            JsonNode targetValue = targetSnapshot.at(pointer);

            boolean sourceMissing = sourceValue.isMissingNode() || sourceValue.isNull();
            boolean targetMissing = targetValue.isMissingNode() || targetValue.isNull();

            if (sourceMissing && targetMissing) {
                continue;
            }

            if (sourceMissing && !targetMissing) {
                return pointer;
            }

            if (!sourceValue.equals(targetValue)) {
                return pointer;
            }
        }

        return null;
    }

    private boolean isLegacyEmptySnapshot(JsonNode snapshot) {
        return snapshot.isObject() && snapshot.isEmpty();
    }

    private String findBreakingItemRuleChange(JsonNode sourceSnapshot, JsonNode targetSnapshot) {
        Map<String, JsonNode> sourceItems = extractLearningItems(sourceSnapshot);
        if (sourceItems.isEmpty()) {
            return null;
        }

        Map<String, JsonNode> targetItems = extractLearningItems(targetSnapshot);

        for (Map.Entry<String, JsonNode> sourceEntry : sourceItems.entrySet()) {
            String itemKey = sourceEntry.getKey();
            JsonNode sourceItem = sourceEntry.getValue();
            JsonNode targetItem = targetItems.get(itemKey);

            if (targetItem == null || targetItem.isMissingNode()) {
                // Missing target item is already considered breaking via removal check.
                continue;
            }

            if (itemKey.startsWith("quiz:")) {
                String changedPointer = findAnyRuleChange(sourceItem, targetItem, QUIZ_BREAKING_RULE_POINTERS);
                if (changedPointer != null) {
                    return itemKey + changedPointer;
                }
            }

            if (itemKey.startsWith("assignment:")) {
                String changedPointer = findAnyRuleChange(sourceItem, targetItem, ASSIGNMENT_BREAKING_RULE_POINTERS);
                if (changedPointer != null) {
                    return itemKey + changedPointer;
                }
            }

            if (itemKey.startsWith("lesson:")) {
                String changedPointer = findAnyRuleChange(sourceItem, targetItem, LESSON_BREAKING_RULE_POINTERS);
                if (changedPointer != null) {
                    return itemKey + changedPointer;
                }
            }
        }

        return null;
    }

    private String findAnyRuleChange(JsonNode sourceNode, JsonNode targetNode, List<String> rulePointers) {
        for (String pointer : rulePointers) {
            if (hasRuleValueChanged(sourceNode, targetNode, pointer)) {
                return pointer;
            }
        }
        return null;
    }

    private boolean hasRuleValueChanged(JsonNode sourceNode, JsonNode targetNode, String pointer) {
        JsonNode sourceValue = sourceNode.at(pointer);
        JsonNode targetValue = targetNode.at(pointer);

        boolean sourceMissing = sourceValue.isMissingNode() || sourceValue.isNull();
        boolean targetMissing = targetValue.isMissingNode() || targetValue.isNull();

        if (sourceMissing && targetMissing) {
            return false;
        }

        return !sourceValue.equals(targetValue);
    }

    private String findRemovedLearningItem(JsonNode sourceSnapshot, JsonNode targetSnapshot) {
        Set<String> sourceItems = extractLearningItemIds(sourceSnapshot);
        if (sourceItems.isEmpty()) {
            return null;
        }

        Set<String> targetItems = extractLearningItemIds(targetSnapshot);
        for (String sourceItem : sourceItems) {
            if (!targetItems.contains(sourceItem)) {
                return sourceItem;
            }
        }
        return null;
    }

    private Set<String> extractLearningItemIds(JsonNode snapshot) {
        Set<String> itemIds = new HashSet<>();

        JsonNode modules = snapshot.at("/modules");
        if (modules.isArray()) {
            modules.forEach(module -> {
                addId(itemIds, "module", module.get("id"));
                collectFromArray(itemIds, "lesson", module.get("lessons"));
                collectFromArray(itemIds, "quiz", module.get("quizzes"));
                collectFromArray(itemIds, "assignment", module.get("assignments"));
            });
        }

        collectFromArray(itemIds, "lesson", snapshot.at("/lessons"));
        collectFromArray(itemIds, "quiz", snapshot.at("/quizzes"));
        collectFromArray(itemIds, "assignment", snapshot.at("/assignments"));

        return itemIds;
    }

    private Map<String, JsonNode> extractLearningItems(JsonNode snapshot) {
        Map<String, JsonNode> items = new HashMap<>();

        JsonNode modules = snapshot.at("/modules");
        if (modules.isArray()) {
            modules.forEach(module -> {
                collectItems(items, "lesson", module.get("lessons"));
                collectItems(items, "quiz", module.get("quizzes"));
                collectItems(items, "assignment", module.get("assignments"));
            });
        }

        collectItems(items, "lesson", snapshot.at("/lessons"));
        collectItems(items, "quiz", snapshot.at("/quizzes"));
        collectItems(items, "assignment", snapshot.at("/assignments"));
        return items;
    }

    private void collectFromArray(Set<String> itemIds, String prefix, JsonNode node) {
        if (node == null || !node.isArray()) {
            return;
        }

        node.forEach(item -> addId(itemIds, prefix, item.get("id")));
    }

    private void collectItems(Map<String, JsonNode> items, String prefix, JsonNode node) {
        if (node == null || !node.isArray()) {
            return;
        }

        node.forEach(item -> {
            JsonNode idNode = item.get("id");
            if (idNode == null || idNode.isNull()) {
                return;
            }
            String id = idNode.asText();
            if (id.isBlank()) {
                return;
            }
            items.putIfAbsent(prefix + ":" + id, item);
        });
    }

    private void addId(Set<String> itemIds, String prefix, JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return;
        }
        itemIds.add(prefix + ":" + idNode.asText());
    }

    public static final class CompatibilityResult {
        private final boolean nonBreaking;
        private final String reasonCode;
        private final String reasonDetail;

        private CompatibilityResult(boolean nonBreaking, String reasonCode, String reasonDetail) {
            this.nonBreaking = nonBreaking;
            this.reasonCode = reasonCode;
            this.reasonDetail = reasonDetail;
        }

        public static CompatibilityResult nonBreaking() {
            return new CompatibilityResult(true, "NON_BREAKING", "compatible");
        }

        public static CompatibilityResult breaking(String reasonCode, String reasonDetail) {
            return new CompatibilityResult(false, reasonCode, reasonDetail);
        }

        public boolean isNonBreaking() {
            return nonBreaking;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String getReasonDetail() {
            return reasonDetail;
        }
    }
}
