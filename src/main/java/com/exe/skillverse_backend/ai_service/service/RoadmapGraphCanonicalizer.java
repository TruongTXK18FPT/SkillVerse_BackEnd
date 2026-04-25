package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Canonicalizes roadmap graph links so FE can rely on parentId/children
 * without having to recover structure heuristically from prerequisites.
 */
final class RoadmapGraphCanonicalizer {

    private RoadmapGraphCanonicalizer() {
    }

    static Result canonicalize(List<RoadmapResponse.RoadmapNode> rawNodes) {
        List<RoadmapResponse.RoadmapNode> nodes = copyNodes(rawNodes);
        Map<String, RoadmapResponse.RoadmapNode> byId = new LinkedHashMap<>();
        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node.getId() != null && !node.getId().isBlank()) {
                byId.put(node.getId(), node);
            }
        }

        Map<String, String> explicitParents = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> incomingParentsByChild = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> normalizedChildren = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int invalidParentRefs = 0;
        int childDerivedParents = 0;
        int conflictingChildRefs = 0;
        int multipleParentCandidates = 0;
        int nodesWithDependencyOnlyHints = 0;

        for (RoadmapResponse.RoadmapNode node : nodes) {
            normalizedChildren.put(node.getId(), new LinkedHashSet<>());
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            String parentId = normalizeId(node.getParentId());
            if (parentId == null) {
                continue;
            }
            if (parentId.equals(node.getId()) || !byId.containsKey(parentId)) {
                invalidParentRefs++;
                warnings.add("Node '" + node.getId() + "' has invalid parentId '" + parentId + "'");
                node.setParentId(null);
                continue;
            }
            explicitParents.put(node.getId(), parentId);
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            for (String childId : safeList(node.getChildren())) {
                String normalizedChildId = normalizeId(childId);
                if (normalizedChildId == null) {
                    continue;
                }
                if (normalizedChildId.equals(node.getId()) || !byId.containsKey(normalizedChildId)) {
                    conflictingChildRefs++;
                    warnings.add("Node '" + node.getId() + "' has invalid child reference '" + childId + "'");
                    continue;
                }
                incomingParentsByChild
                        .computeIfAbsent(normalizedChildId, ignored -> new LinkedHashSet<>())
                        .add(node.getId());
            }
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            String nodeId = node.getId();
            String resolvedParentId = explicitParents.get(nodeId);
            LinkedHashSet<String> candidateParents = incomingParentsByChild.get(nodeId);

            if (resolvedParentId == null && candidateParents != null && !candidateParents.isEmpty()) {
                if (candidateParents.size() > 1) {
                    multipleParentCandidates++;
                    warnings.add("Node '" + nodeId + "' is referenced by multiple parents "
                            + candidateParents + "; using first reference by order");
                }
                resolvedParentId = candidateParents.iterator().next();
                explicitParents.put(nodeId, resolvedParentId);
                childDerivedParents++;
            } else if (resolvedParentId != null && candidateParents != null && !candidateParents.isEmpty()
                    && !candidateParents.contains(resolvedParentId)) {
                conflictingChildRefs++;
                warnings.add("Node '" + nodeId + "' parentId '" + resolvedParentId
                        + "' conflicts with children refs " + candidateParents);
            }

            if (resolvedParentId == null
                    && !safeList(node.getPrerequisites()).isEmpty()
                    && (candidateParents == null || candidateParents.isEmpty())) {
                nodesWithDependencyOnlyHints++;
                warnings.add("Node '" + nodeId
                        + "' has prerequisites but no tree parent/child links; preserving as root-like node");
            }
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            String resolvedParentId = explicitParents.get(node.getId());
            node.setParentId(resolvedParentId);
            if (resolvedParentId != null) {
                normalizedChildren.get(resolvedParentId).add(node.getId());
            }
        }

        int rootCount = 0;
        int branchCount = 0;
        for (RoadmapResponse.RoadmapNode node : nodes) {
            List<String> canonicalChildren = new ArrayList<>(normalizedChildren.get(node.getId()));
            node.setChildren(canonicalChildren);
            if (node.getParentId() == null) {
                rootCount++;
            } else {
                branchCount++;
            }
        }

        return new Result(
                nodes,
                warnings,
                rootCount,
                branchCount,
                childDerivedParents,
                invalidParentRefs,
                conflictingChildRefs,
                multipleParentCandidates,
                nodesWithDependencyOnlyHints);
    }

    private static List<RoadmapResponse.RoadmapNode> copyNodes(List<RoadmapResponse.RoadmapNode> rawNodes) {
        List<RoadmapResponse.RoadmapNode> copies = new ArrayList<>();
        for (RoadmapResponse.RoadmapNode node : rawNodes) {
            copies.add(RoadmapResponse.RoadmapNode.builder()
                    .id(node.getId())
                    .title(node.getTitle())
                    .description(node.getDescription())
                    .estimatedTimeMinutes(node.getEstimatedTimeMinutes())
                    .type(node.getType())
                    .difficulty(node.getDifficulty())
                    .phaseId(node.getPhaseId())
                    .orderIndex(node.getOrderIndex())
                    .mainPathIndex(node.getMainPathIndex())
                    .isCore(node.getIsCore())
                    .parentId(node.getParentId())
                    .suggestedCourseIds(new ArrayList<>(safeList(node.getSuggestedCourseIds())))
                    .suggestedModuleIds(new ArrayList<>(safeList(node.getSuggestedModuleIds())))
                    .nodeStatus(node.getNodeStatus())
                    .learningObjectives(new ArrayList<>(safeList(node.getLearningObjectives())))
                    .keyConcepts(new ArrayList<>(safeList(node.getKeyConcepts())))
                    .practicalExercises(new ArrayList<>(safeList(node.getPracticalExercises())))
                    .suggestedResources(new ArrayList<>(safeList(node.getSuggestedResources())))
                    .successCriteria(new ArrayList<>(safeList(node.getSuccessCriteria())))
                    .prerequisites(new ArrayList<>(safeList(node.getPrerequisites())))
                    .children(new ArrayList<>(safeList(node.getChildren())))
                    .estimatedCompletionRate(node.getEstimatedCompletionRate())
                    .build());
        }
        return copies;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : Collections.emptyList();
    }

    static final class Result {
        private final List<RoadmapResponse.RoadmapNode> nodes;
        private final List<String> warnings;
        private final int rootCount;
        private final int branchCount;
        private final int childDerivedParents;
        private final int invalidParentRefs;
        private final int conflictingChildRefs;
        private final int multipleParentCandidates;
        private final int dependencyOnlyNodes;

        Result(
                List<RoadmapResponse.RoadmapNode> nodes,
                List<String> warnings,
                int rootCount,
                int branchCount,
                int childDerivedParents,
                int invalidParentRefs,
                int conflictingChildRefs,
                int multipleParentCandidates,
                int dependencyOnlyNodes) {
            this.nodes = nodes;
            this.warnings = warnings;
            this.rootCount = rootCount;
            this.branchCount = branchCount;
            this.childDerivedParents = childDerivedParents;
            this.invalidParentRefs = invalidParentRefs;
            this.conflictingChildRefs = conflictingChildRefs;
            this.multipleParentCandidates = multipleParentCandidates;
            this.dependencyOnlyNodes = dependencyOnlyNodes;
        }

        List<RoadmapResponse.RoadmapNode> nodes() {
            return nodes;
        }

        List<String> warnings() {
            return warnings;
        }

        int rootCount() {
            return rootCount;
        }

        int branchCount() {
            return branchCount;
        }

        int childDerivedParents() {
            return childDerivedParents;
        }

        int invalidParentRefs() {
            return invalidParentRefs;
        }

        int conflictingChildRefs() {
            return conflictingChildRefs;
        }

        int multipleParentCandidates() {
            return multipleParentCandidates;
        }

        int dependencyOnlyNodes() {
            return dependencyOnlyNodes;
        }
    }
}
