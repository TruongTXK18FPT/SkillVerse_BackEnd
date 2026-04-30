package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stateless scorer for roadmap node importance.
 *
 * <p>All methods are package-private static — no Spring bean, no state.
 * Called exclusively from {@code AiRoadmapServiceImpl} at Step 4.5.
 */
final class RoadmapImportanceScorer {

    private RoadmapImportanceScorer() {}

    /**
     * Validate AI importance scores against a deterministic backend heuristic,
     * then set finalScore and importanceValidationStatus on every node.
     *
     * <p>Decision tree:
     * <pre>
     *   aiScore == null
     *       → FALLBACK: use backendScore, confidenceScore = 0.50
     *
     *   delta > 0.20  (AI and backend disagree)
     *       → ADJUSTED: blend (aiScore×0.40 + backendScore×0.60), confidence −0.25
     *
     *   delta ≤ 0.20, missing evidence or reason
     *       → LOW_CONFIDENCE: use aiScore, confidence −0.20
     *
     *   delta ≤ 0.20, has evidence + reason
     *       → ACCEPTED: use aiScore as-is, confidence = aiConfidence ?? 0.75
     * </pre>
     */
    static void validateAndBackfill(
            List<RoadmapResponse.RoadmapNode> nodes,
            GenerateRoadmapRequest request) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (RoadmapResponse.RoadmapNode node : nodes) {
            Double aiScore = node.getImportanceScore();
            Double aiConfidence = node.getConfidenceScore();
            boolean hasEvidence = node.getEvidence() != null && !node.getEvidence().isEmpty();
            boolean hasReason = node.getReason() != null && !node.getReason().isBlank();

            double backendScore = computeBackendScore(node, request);

            if (aiScore == null) {
                node.setImportanceScore(backendScore);
                node.setConfidenceScore(0.50);
                node.setImportanceValidationStatus(
                        RoadmapResponse.RoadmapNode.ImportanceValidationStatus.FALLBACK.name());

            } else {
                double delta = Math.abs(aiScore - backendScore);

                if (delta > 0.20) {
                    double blended = (aiScore * 0.40) + (backendScore * 0.60);
                    node.setImportanceScore(Math.min(1.0, blended));
                    node.setConfidenceScore(
                            Math.max(0.10, (aiConfidence != null ? aiConfidence : 0.50) - 0.25));
                    node.setImportanceValidationStatus(
                            RoadmapResponse.RoadmapNode.ImportanceValidationStatus.ADJUSTED.name());

                } else if (!hasEvidence || !hasReason) {
                    node.setImportanceScore(aiScore);
                    node.setConfidenceScore(
                            Math.max(0.10, (aiConfidence != null ? aiConfidence : 0.50) - 0.20));
                    node.setImportanceValidationStatus(
                            RoadmapResponse.RoadmapNode.ImportanceValidationStatus.LOW_CONFIDENCE.name());

                } else {
                    node.setImportanceScore(aiScore);
                    node.setConfidenceScore(aiConfidence != null ? aiConfidence : 0.75);
                    node.setImportanceValidationStatus(
                            RoadmapResponse.RoadmapNode.ImportanceValidationStatus.ACCEPTED.name());
                }
            }

            if (node.getReason() == null || node.getReason().isBlank()) {
                node.setReason(buildFallbackReason(node));
            }
            if (node.getEvidence() == null || node.getEvidence().isEmpty()) {
                node.setEvidence(buildFallbackEvidence(node, request));
            }
        }
    }

    /**
     * Compute a deterministic backend importance score from structural node signals.
     *
     * <p>Signals:
     * <ul>
     *   <li>type            — MAIN (+0.30) / SIDE (+0.10)
     *   <li>difficulty      — hard/expert (+0.20), medium (+0.12), easy (+0.05)
     *   <li>prereqCount     — 0 prereqs = foundational (+0.10); 1 prereq (+0.05); 2+ = no bonus
     *   <li>childCount      — more nodes unlocked = higher structural value (+0.04 each, max 0.15)
     *   <li>objectivesCount — richer content signals deliberate importance (+0.03 each, max 0.10)
     *   <li>skillGapMatch   — title/description overlaps difficultyConcern/background (+0.10)
     * </ul>
     */
    static double computeBackendScore(
            RoadmapResponse.RoadmapNode node, GenerateRoadmapRequest request) {
        double score = 0.0;

        // Signal 1: structural role
        if (node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN) {
            score += 0.30;
        } else {
            score += 0.10;
        }

        // Signal 2: difficulty
        String diff = node.getDifficulty() != null
                ? node.getDifficulty().toLowerCase(Locale.ROOT) : "";
        if (diff.equals("hard") || diff.equals("expert")) {
            score += 0.20;
        } else if (diff.equals("medium")) {
            score += 0.12;
        } else {
            score += 0.05;
        }

        // Signal 3: foundational position
        int prereqCount = node.getPrerequisites() != null ? node.getPrerequisites().size() : 0;
        if (prereqCount == 0) {
            score += 0.10;
        } else if (prereqCount == 1) {
            score += 0.05;
        }

        // Signal 4: breadth
        int childCount = node.getChildren() != null ? node.getChildren().size() : 0;
        score += Math.min(0.15, childCount * 0.04);

        // Signal 5: content richness
        int objectivesCount = node.getLearningObjectives() != null
                ? node.getLearningObjectives().size() : 0;
        score += Math.min(0.10, objectivesCount * 0.03);

        // Signal 6: skill-gap relevance
        if (matchesSkillGapSignal(node, request)) {
            score += 0.10;
        }

        return Math.min(1.0, score);
    }

    /** Returns true if node title/description contains a keyword from request skill-gap fields. */
    static boolean matchesSkillGapSignal(
            RoadmapResponse.RoadmapNode node, GenerateRoadmapRequest request) {
        String nodeText = ((node.getTitle() != null ? node.getTitle() : "") + " "
                + (node.getDescription() != null ? node.getDescription() : ""))
                .toLowerCase(Locale.ROOT);
        if (nodeText.isBlank()) {
            return false;
        }
        String concern = request.getDifficultyConcern();
        if (concern != null && !concern.isBlank()) {
            for (String token : concern.toLowerCase(Locale.ROOT).split("[\\s,;]+")) {
                if (token.length() >= 3 && nodeText.contains(token)) {
                    return true;
                }
            }
        }
        String background = request.getBackground();
        if (background != null && background.length() > 4) {
            String bg = background.substring(0, Math.min(120, background.length()))
                    .toLowerCase(Locale.ROOT);
            for (String token : bg.split("[\\s,;:]+")) {
                if (token.length() >= 4 && nodeText.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String buildFallbackReason(RoadmapResponse.RoadmapNode node) {
        boolean isMain = node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN;
        int childCount = node.getChildren() != null ? node.getChildren().size() : 0;
        if (childCount > 0) {
            return String.format("%s kỹ năng bắt buộc, mở khóa %d node tiếp theo.",
                    isMain ? "Nền tảng" : "Bổ trợ", childCount);
        }
        return isMain ? "Node bắt buộc trên lộ trình học chính." : "Node bổ sung tùy chọn.";
    }

    private static List<String> buildFallbackEvidence(
            RoadmapResponse.RoadmapNode node, GenerateRoadmapRequest request) {
        List<String> ev = new ArrayList<>();
        if (node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN) {
            ev.add("Loại node: MAIN (bắt buộc trong lộ trình chính)");
        }
        int prereqCount = node.getPrerequisites() != null ? node.getPrerequisites().size() : 0;
        if (prereqCount > 0) {
            ev.add("Cần hoàn thành trước: " + prereqCount + " node tiên quyết");
        }
        if (matchesSkillGapSignal(node, request)) {
            ev.add("Đáp ứng skill gap hoặc điểm khó đã đánh giá");
        }
        return ev;
    }
}
