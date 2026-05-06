package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces SkillVerse roadmap branching semantics:
 * MAIN nodes form the required learning spine, SIDE nodes are optional support
 * branches attached to one MAIN parent.
 */
final class RoadmapBranchingNormalizer {

    private static final int REQUIRED_MAIN_NODES = 8;
    private static final int MIN_SIDE_NODES = 3;
    private static final int MAX_SIDE_NODES = 6;

    private RoadmapBranchingNormalizer() {
    }

    static Result normalize(List<RoadmapResponse.RoadmapNode> rawNodes) {
        List<String> warnings = new ArrayList<>();
        if (rawNodes == null || rawNodes.isEmpty()) {
            return new Result(List.of(), warnings, 0, 0);
        }

        List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>(rawNodes);
        Map<String, Integer> originalOrder = new HashMap<>();
        Map<String, RoadmapResponse.RoadmapNode> byId = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            RoadmapResponse.RoadmapNode node = nodes.get(i);
            if (node == null || isBlank(node.getId())) {
                warnings.add("Roadmap contains a node without id; keeping original relative order");
                continue;
            }
            originalOrder.put(node.getId(), i);
            byId.put(node.getId(), node);
        }

        if (byId.isEmpty()) {
            return new Result(nodes, warnings, 0, 0);
        }
        boolean enforceStandardCounts = byId.size() >= REQUIRED_MAIN_NODES + MIN_SIDE_NODES;

        List<RoadmapResponse.RoadmapNode> mainNodes = nodes.stream()
                .filter(node -> node != null && byId.containsKey(node.getId()))
                .filter(RoadmapBranchingNormalizer::isMainNode)
                .toList();

        if (mainNodes.isEmpty()) {
            RoadmapResponse.RoadmapNode firstNode = byId.values().iterator().next();
            firstNode.setType(RoadmapResponse.RoadmapNode.NodeType.MAIN);
            firstNode.setIsCore(true);
            mainNodes = List.of(firstNode);
            warnings.add("No MAIN node found; promoted first node '" + firstNode.getId() + "' to MAIN");
        }

        if (enforceStandardCounts && mainNodes.size() < REQUIRED_MAIN_NODES) {
            List<RoadmapResponse.RoadmapNode> expandedMainNodes = new ArrayList<>(mainNodes);
            Set<String> mainIds = new HashSet<>();
            for (RoadmapResponse.RoadmapNode node : mainNodes) {
                mainIds.add(node.getId());
            }
            for (RoadmapResponse.RoadmapNode node : nodes) {
                if (expandedMainNodes.size() >= REQUIRED_MAIN_NODES) {
                    break;
                }
                if (node == null || !byId.containsKey(node.getId()) || mainIds.contains(node.getId())) {
                    continue;
                }
                node.setType(RoadmapResponse.RoadmapNode.NodeType.MAIN);
                node.setIsCore(true);
                expandedMainNodes.add(node);
                mainIds.add(node.getId());
                warnings.add("Promoted node '" + node.getId() + "' to MAIN to satisfy 8 MAIN nodes");
            }
            mainNodes = expandedMainNodes;
        }

        List<RoadmapResponse.RoadmapNode> orderedMain = topologicalSortMain(mainNodes, byId, originalOrder, warnings);
        List<RoadmapResponse.RoadmapNode> overflowMainNodes = new ArrayList<>();
        if (enforceStandardCounts && orderedMain.size() > REQUIRED_MAIN_NODES) {
            overflowMainNodes.addAll(orderedMain.subList(REQUIRED_MAIN_NODES, orderedMain.size()));
            orderedMain = new ArrayList<>(orderedMain.subList(0, REQUIRED_MAIN_NODES));
            warnings.add("Trimmed MAIN spine to exactly 8 nodes; overflow MAIN nodes became SIDE candidates");
        }

        Map<String, Integer> mainPositionById = new LinkedHashMap<>();
        for (int i = 0; i < orderedMain.size(); i++) {
            RoadmapResponse.RoadmapNode node = orderedMain.get(i);
            mainPositionById.put(node.getId(), i);
            node.setType(RoadmapResponse.RoadmapNode.NodeType.MAIN);
            node.setIsCore(true);
            node.setMainPathIndex(i + 1);
            node.setOrderIndex(i + 1);
            node.setParentId(i == 0 ? null : orderedMain.get(i - 1).getId());
            node.setPrerequisites(i == 0 ? new ArrayList<>() : new ArrayList<>(List.of(orderedMain.get(i - 1).getId())));
            node.setChildren(new ArrayList<>());
        }

        Map<String, List<RoadmapResponse.RoadmapNode>> sideNodesByMain = new LinkedHashMap<>();
        for (RoadmapResponse.RoadmapNode main : orderedMain) {
            sideNodesByMain.put(main.getId(), new ArrayList<>());
        }

        int acceptedSideNodes = 0;
        Set<String> acceptedOverflowSideIds = new HashSet<>();
        for (RoadmapResponse.RoadmapNode node : overflowMainNodes) {
            if (enforceStandardCounts && acceptedSideNodes >= MAX_SIDE_NODES) {
                warnings.add("Dropped overflow MAIN node '" + node.getId() + "' to keep at most 6 SIDE nodes");
                continue;
            }
            RoadmapResponse.RoadmapNode parentMain = resolveSideParent(node, orderedMain, mainPositionById, byId, originalOrder);
            if (parentMain == null) {
                parentMain = orderedMain.get(orderedMain.size() - 1);
            }
            attachSideNode(node, parentMain, orderedMain.size(), originalOrder, nodes.size());
            sideNodesByMain.get(parentMain.getId()).add(node);
            acceptedOverflowSideIds.add(node.getId());
            acceptedSideNodes++;
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || !byId.containsKey(node.getId()) || mainPositionById.containsKey(node.getId())) {
                continue;
            }
            if (acceptedOverflowSideIds.contains(node.getId())) {
                continue;
            }
            if (enforceStandardCounts && acceptedSideNodes >= MAX_SIDE_NODES) {
                warnings.add("Dropped SIDE node '" + node.getId() + "' to keep at most 6 SIDE nodes");
                continue;
            }

            RoadmapResponse.RoadmapNode parentMain = resolveSideParent(node, orderedMain, mainPositionById, byId, originalOrder);
            if (parentMain == null) {
                parentMain = orderedMain.get(0);
                warnings.add("SIDE node '" + node.getId() + "' had no MAIN parent candidate; attached to first MAIN");
            }

            attachSideNode(node, parentMain, orderedMain.size(), originalOrder, nodes.size());
            sideNodesByMain.get(parentMain.getId()).add(node);
            acceptedSideNodes++;
        }

        if (enforceStandardCounts && acceptedSideNodes < MIN_SIDE_NODES && nodes.size() > orderedMain.size()) {
            warnings.add("Roadmap did not provide enough SIDE candidates to satisfy at least 3 SIDE nodes");
        }

        List<RoadmapResponse.RoadmapNode> normalized = new ArrayList<>(nodes.size());
        normalized.addAll(orderedMain);

        int nextOrderIndex = orderedMain.size() + 1;
        for (int i = 0; i < orderedMain.size(); i++) {
            RoadmapResponse.RoadmapNode main = orderedMain.get(i);
            List<String> children = new ArrayList<>();
            if (i + 1 < orderedMain.size()) {
                children.add(orderedMain.get(i + 1).getId());
            }

            List<RoadmapResponse.RoadmapNode> sideNodes = sideNodesByMain.getOrDefault(main.getId(), List.of());
            sideNodes.sort(Comparator
                    .comparing((RoadmapResponse.RoadmapNode node) -> originalOrder.getOrDefault(node.getId(), Integer.MAX_VALUE))
                    .thenComparing(RoadmapResponse.RoadmapNode::getId, Comparator.nullsLast(String::compareTo)));
            for (RoadmapResponse.RoadmapNode side : sideNodes) {
                side.setOrderIndex(nextOrderIndex++);
                children.add(side.getId());
                normalized.add(side);
            }
            main.setChildren(children);
        }

        int sideCount = normalized.size() - orderedMain.size();
        return new Result(normalized, warnings, orderedMain.size(), sideCount);
    }

    private static void attachSideNode(
            RoadmapResponse.RoadmapNode node,
            RoadmapResponse.RoadmapNode parentMain,
            int mainNodeCount,
            Map<String, Integer> originalOrder,
            int fallbackOrder) {
        node.setType(RoadmapResponse.RoadmapNode.NodeType.SIDE);
        node.setIsCore(false);
        node.setParentId(parentMain.getId());
        node.setMainPathIndex(parentMain.getMainPathIndex());
        node.setOrderIndex(mainNodeCount + originalOrder.getOrDefault(node.getId(), fallbackOrder) + 1);
        if (isBlank(node.getPhaseId())) {
            node.setPhaseId(parentMain.getPhaseId());
        }
        node.setPrerequisites(new ArrayList<>(List.of(parentMain.getId())));
        node.setChildren(new ArrayList<>());
    }

    private static boolean isMainNode(RoadmapResponse.RoadmapNode node) {
        if (node == null) {
            return false;
        }
        return node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN || Boolean.TRUE.equals(node.getIsCore());
    }

    private static List<RoadmapResponse.RoadmapNode> topologicalSortMain(
            List<RoadmapResponse.RoadmapNode> mainNodes,
            Map<String, RoadmapResponse.RoadmapNode> byId,
            Map<String, Integer> originalOrder,
            List<String> warnings) {

        Set<String> mainIds = new LinkedHashSet<>();
        for (RoadmapResponse.RoadmapNode node : mainNodes) {
            if (!isBlank(node.getId())) {
                mainIds.add(node.getId());
            }
        }

        Map<String, Set<String>> remainingDeps = new LinkedHashMap<>();
        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        for (String id : mainIds) {
            remainingDeps.put(id, new LinkedHashSet<>());
            dependents.put(id, new LinkedHashSet<>());
        }

        for (RoadmapResponse.RoadmapNode node : mainNodes) {
            String nodeId = node.getId();
            for (String prereqId : safeList(node.getPrerequisites())) {
                if (mainIds.contains(prereqId) && !prereqId.equals(nodeId)) {
                    remainingDeps.get(nodeId).add(prereqId);
                    dependents.get(prereqId).add(nodeId);
                }
            }
        }

        Comparator<RoadmapResponse.RoadmapNode> comparator = Comparator
                .comparing((RoadmapResponse.RoadmapNode node) -> normalizedIndex(node.getMainPathIndex()))
                .thenComparing(node -> normalizedIndex(node.getOrderIndex()))
                .thenComparing(node -> originalOrder.getOrDefault(node.getId(), Integer.MAX_VALUE))
                .thenComparing(RoadmapResponse.RoadmapNode::getId, Comparator.nullsLast(String::compareTo));

        List<RoadmapResponse.RoadmapNode> ready = mainNodes.stream()
                .filter(node -> remainingDeps.getOrDefault(node.getId(), Set.of()).isEmpty())
                .sorted(comparator)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        List<RoadmapResponse.RoadmapNode> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();

        while (!ready.isEmpty()) {
            RoadmapResponse.RoadmapNode current = ready.remove(0);
            if (!emitted.add(current.getId())) {
                continue;
            }
            ordered.add(current);

            for (String dependentId : dependents.getOrDefault(current.getId(), Set.of())) {
                Set<String> deps = remainingDeps.get(dependentId);
                if (deps == null) {
                    continue;
                }
                deps.remove(current.getId());
                if (deps.isEmpty() && !emitted.contains(dependentId)) {
                    RoadmapResponse.RoadmapNode dependent = byId.get(dependentId);
                    if (dependent != null) {
                        ready.add(dependent);
                        ready.sort(comparator);
                    }
                }
            }
        }

        if (ordered.size() != mainIds.size()) {
            warnings.add("Cycle or unresolved MAIN prerequisites detected; appended remaining MAIN nodes by display order");
            mainNodes.stream()
                    .filter(node -> !emitted.contains(node.getId()))
                    .sorted(comparator)
                    .forEach(ordered::add);
        }

        return ordered;
    }

    private static RoadmapResponse.RoadmapNode resolveSideParent(
            RoadmapResponse.RoadmapNode side,
            List<RoadmapResponse.RoadmapNode> orderedMain,
            Map<String, Integer> mainPositionById,
            Map<String, RoadmapResponse.RoadmapNode> byId,
            Map<String, Integer> originalOrder) {

        String parentId = normalizeId(side.getParentId());
        if (parentId != null && mainPositionById.containsKey(parentId)) {
            return byId.get(parentId);
        }

        RoadmapResponse.RoadmapNode bestPrereqMain = null;
        int bestPrereqIndex = -1;
        for (String prereqId : safeList(side.getPrerequisites())) {
            Integer mainIndex = mainPositionById.get(prereqId);
            if (mainIndex != null && mainIndex > bestPrereqIndex) {
                bestPrereqIndex = mainIndex;
                bestPrereqMain = byId.get(prereqId);
            }
        }
        if (bestPrereqMain != null) {
            return bestPrereqMain;
        }

        int sideOriginalIndex = originalOrder.getOrDefault(side.getId(), Integer.MAX_VALUE);
        RoadmapResponse.RoadmapNode closestPreviousMain = null;
        int closestPreviousIndex = -1;
        for (RoadmapResponse.RoadmapNode main : orderedMain) {
            int mainOriginalIndex = originalOrder.getOrDefault(main.getId(), Integer.MAX_VALUE);
            if (mainOriginalIndex <= sideOriginalIndex && mainOriginalIndex > closestPreviousIndex) {
                closestPreviousIndex = mainOriginalIndex;
                closestPreviousMain = main;
            }
        }
        return closestPreviousMain != null ? closestPreviousMain : orderedMain.get(0);
    }

    private static int normalizedIndex(Integer value) {
        return value != null && value > 0 ? value : Integer.MAX_VALUE;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    static final class Result {
        private final List<RoadmapResponse.RoadmapNode> nodes;
        private final List<String> warnings;
        private final int mainNodes;
        private final int sideNodes;

        Result(
                List<RoadmapResponse.RoadmapNode> nodes,
                List<String> warnings,
                int mainNodes,
                int sideNodes) {
            this.nodes = nodes;
            this.warnings = warnings;
            this.mainNodes = mainNodes;
            this.sideNodes = sideNodes;
        }

        List<RoadmapResponse.RoadmapNode> nodes() {
            return nodes;
        }

        List<String> warnings() {
            return warnings;
        }

        int mainNodes() {
            return mainNodes;
        }

        int sideNodes() {
            return sideNodes;
        }
    }
}
