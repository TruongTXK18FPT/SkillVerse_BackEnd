package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.List;
import java.util.Map;

/**
 * Stateless calculator for weighted roadmap progress.
 *
 * <p>Formula: nodeWeight = 0.4 + normalizedImportanceScore * 0.6
 * <br>fallback importanceScore = 0.5 (yields weight 0.7) for null/NaN/Infinity.
 *
 * <p>All methods are package-private static — no Spring bean, no state.
 */
final class RoadmapProgressCalculator {

    static final double FALLBACK_IMPORTANCE_SCORE = 0.5;
    static final double MIN_NODE_WEIGHT = 0.4;
    static final double IMPORTANCE_WEIGHT_RANGE = 0.6;

    private RoadmapProgressCalculator() {}

    /**
     * Calculate weighted roadmap progress.
     *
     * @param nodes       all roadmap nodes (may be empty, not null)
     * @param progressMap quest-id to QuestProgress map (may be empty, not null)
     * @return calculation result — all fields are zero when no valid nodes exist
     */
    static ProgressCalculation calculate(
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> progressMap) {
        int totalQuests = 0;
        int completedQuests = 0;
        double completedWeight = 0.0;
        double totalWeight = 0.0;

        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            totalQuests++;
            double weight = resolveWeight(node.getImportanceScore());
            totalWeight += weight;

            RoadmapResponse.QuestProgress qp = progressMap.get(node.getId());
            if (qp != null && "COMPLETED".equals(qp.getStatus())) {
                completedQuests++;
                completedWeight += weight;
            }
        }

        double completionPercentage = totalWeight > 0.0
                ? completedWeight / totalWeight * 100.0
                : 0.0;

        return new ProgressCalculation(
                totalQuests, completedQuests, completionPercentage, completedWeight, totalWeight);
    }

    /**
     * Resolve node weight from importanceScore.
     * Null, NaN, or Infinity fall back to {@code FALLBACK_IMPORTANCE_SCORE}.
     */
    static double resolveWeight(Double importanceScore) {
        double normalized;
        if (importanceScore == null || !Double.isFinite(importanceScore)) {
            normalized = FALLBACK_IMPORTANCE_SCORE;
        } else {
            normalized = Math.max(0.0, Math.min(1.0, importanceScore));
        }
        return MIN_NODE_WEIGHT + normalized * IMPORTANCE_WEIGHT_RANGE;
    }

    record ProgressCalculation(
            int totalQuests,
            int completedQuests,
            double completionPercentage,
            double completedWeight,
            double totalWeight) {}
}
