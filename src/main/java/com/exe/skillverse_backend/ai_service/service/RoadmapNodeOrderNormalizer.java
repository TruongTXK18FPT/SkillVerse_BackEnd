package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless ordering normalizer for roadmap nodes.
 *
 * <p>All methods are package-private static — no Spring bean, no state.
 * Called exclusively from {@code AiRoadmapServiceImpl} at Step 4.6.
 */
final class RoadmapNodeOrderNormalizer {

    private RoadmapNodeOrderNormalizer() {}

    /**
     * Normalize orderIndex then apply importance-aware stable ordering.
     *
     * <p>Pass 1 — Assign missing orderIndex:
     * Any node whose orderIndex is null gets the value (1 + its current list position).
     * This makes the original AI ordering the baseline when the AI omitted order_index.
     *
     * <p>Pass 2 — Sort with three-level comparator:
     * <ul>
     *   <li>Primary   : orderIndex ascending  (preserves AI learning sequence)
     *   <li>Secondary : importanceScore descending (higher-importance node first among ties)
     *   <li>Tertiary  : original list position (stable tie-break, no arbitrary reordering)
     * </ul>
     *
     * <p>Pass 3 — Prerequisite guard:
     * After sorting, a single forward scan checks that each node's prerequisites appear
     * before it in the final list. If any prerequisite is found after the current node,
     * the node is moved to just after its last missing prerequisite.
     * O(n²) worst-case but n ≤ 15 for MVP — no full topological sort needed.
     * Cycle guard: if a node is displaced more than n times, circular dependency is detected
     * and the node is accepted at its current position rather than hanging the thread.
     */
    static void normalize(List<RoadmapResponse.RoadmapNode> nodes) {
        if (nodes == null || nodes.size() <= 1) {
            return;
        }

        // Pass 1: assign stable orderIndex where missing
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getOrderIndex() == null) {
                nodes.get(i).setOrderIndex(i + 1);
            }
        }

        // Capture original positions for the stable tertiary tiebreak
        Map<String, Integer> originalPosition = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String id = nodes.get(i).getId();
            if (id != null) {
                originalPosition.put(id, i);
            }
        }

        // Pass 2: sort by (orderIndex ASC, importanceScore DESC, originalPosition ASC)
        nodes.sort((a, b) -> {
            int oa = a.getOrderIndex() != null ? a.getOrderIndex() : Integer.MAX_VALUE;
            int ob = b.getOrderIndex() != null ? b.getOrderIndex() : Integer.MAX_VALUE;
            if (oa != ob) {
                return Integer.compare(oa, ob);
            }
            double ia = a.getImportanceScore() != null ? a.getImportanceScore() : 0.0;
            double ib = b.getImportanceScore() != null ? b.getImportanceScore() : 0.0;
            if (Double.compare(ia, ib) != 0) {
                return Double.compare(ib, ia); // descending
            }
            int pa = originalPosition.getOrDefault(a.getId(), Integer.MAX_VALUE);
            int pb = originalPosition.getOrDefault(b.getId(), Integer.MAX_VALUE);
            return Integer.compare(pa, pb);
        });

        // Pass 3: prerequisite guard with cycle detection
        Map<String, Integer> displaceCount = new HashMap<>();
        int maxDisplacements = nodes.size();

        Set<String> placed = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            RoadmapResponse.RoadmapNode node = nodes.get(i);
            List<String> prereqs = node.getPrerequisites();
            if (prereqs == null || prereqs.isEmpty()) {
                placed.add(node.getId());
                continue;
            }
            int lastMissingAt = -1;
            for (String prereqId : prereqs) {
                if (!placed.contains(prereqId)) {
                    for (int j = i + 1; j < nodes.size(); j++) {
                        if (prereqId.equals(nodes.get(j).getId())) {
                            lastMissingAt = Math.max(lastMissingAt, j);
                            break;
                        }
                    }
                }
            }
            if (lastMissingAt > i) {
                String nodeId = node.getId() != null ? node.getId() : "";
                int count = displaceCount.getOrDefault(nodeId, 0) + 1;
                if (count > maxDisplacements) {
                    placed.add(nodeId);
                    continue;
                }
                displaceCount.put(nodeId, count);
                RoadmapResponse.RoadmapNode displaced = nodes.remove(i);
                nodes.add(lastMissingAt, displaced);
                i--;
            } else {
                placed.add(node.getId());
            }
        }
    }
}
