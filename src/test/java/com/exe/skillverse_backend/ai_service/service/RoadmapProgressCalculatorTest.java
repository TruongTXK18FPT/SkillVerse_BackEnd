package com.exe.skillverse_backend.ai_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoadmapProgressCalculatorTest {

    private static final double DELTA = 0.01;

    // --- helpers ---

    private static RoadmapResponse.RoadmapNode node(String id, Double importanceScore) {
        RoadmapResponse.RoadmapNode n = new RoadmapResponse.RoadmapNode();
        n.setId(id);
        n.setImportanceScore(importanceScore);
        return n;
    }

    private static RoadmapResponse.QuestProgress completed(String id) {
        return RoadmapResponse.QuestProgress.builder()
                .questId(id)
                .status("COMPLETED")
                .progress(100)
                .build();
    }

    private static RoadmapResponse.QuestProgress notStarted(String id) {
        return RoadmapResponse.QuestProgress.builder()
                .questId(id)
                .status("NOT_STARTED")
                .progress(0)
                .build();
    }

    // --- scenario 1: equal fallback weights ---

    @Test
    void calculate_equalFallbackWeights_oneOfThreeCompleted() {
        List<RoadmapResponse.RoadmapNode> nodes = List.of(
                node("a", null),
                node("b", null),
                node("c", null));
        Map<String, RoadmapResponse.QuestProgress> progress = Map.of(
                "a", completed("a"),
                "b", notStarted("b"),
                "c", notStarted("c"));

        RoadmapProgressCalculator.ProgressCalculation result =
                RoadmapProgressCalculator.calculate(nodes, progress);

        assertEquals(3, result.totalQuests());
        assertEquals(1, result.completedQuests());
        // weight(null) = 0.4 + 0.5*0.6 = 0.7; 1/3 completed => 33.33%
        assertEquals(33.33, result.completionPercentage(), DELTA);
        assertEquals(0.7, result.completedWeight(), DELTA);
        assertEquals(2.1, result.totalWeight(), DELTA);
    }

    // --- scenario 2: weighted importance ---

    @Test
    void calculate_weightedImportance_highImportanceCompleted() {
        // A: importance 1.0, weight 1.0, completed
        // B: importance 0.0, weight 0.4, not completed
        List<RoadmapResponse.RoadmapNode> nodes = List.of(
                node("a", 1.0),
                node("b", 0.0));
        Map<String, RoadmapResponse.QuestProgress> progress = Map.of(
                "a", completed("a"),
                "b", notStarted("b"));

        RoadmapProgressCalculator.ProgressCalculation result =
                RoadmapProgressCalculator.calculate(nodes, progress);

        assertEquals(2, result.totalQuests());
        assertEquals(1, result.completedQuests());
        // completedWeight=1.0, totalWeight=1.4 => 1.0/1.4*100 = 71.43%
        assertEquals(71.43, result.completionPercentage(), DELTA);
        assertEquals(1.0, result.completedWeight(), DELTA);
        assertEquals(1.4, result.totalWeight(), DELTA);
    }

    // --- scenario 3: null importanceScore uses fallback weight 0.7 ---

    @Test
    void resolveWeight_nullImportance_returnsFallbackWeight() {
        double weight = RoadmapProgressCalculator.resolveWeight(null);
        // 0.4 + 0.5*0.6 = 0.70
        assertEquals(0.70, weight, DELTA);
    }

    // --- scenario 4: non-finite importanceScore uses fallback weight ---

    @Test
    void resolveWeight_nanImportance_returnsFallbackWeight() {
        assertEquals(0.70, RoadmapProgressCalculator.resolveWeight(Double.NaN), DELTA);
    }

    @Test
    void resolveWeight_infinityImportance_returnsFallbackWeight() {
        assertEquals(0.70, RoadmapProgressCalculator.resolveWeight(Double.POSITIVE_INFINITY), DELTA);
    }

    // --- scenario 5: blank node IDs are ignored ---

    @Test
    void calculate_blankNodeIds_areIgnored() {
        List<RoadmapResponse.RoadmapNode> nodes = List.of(
                node("", 0.5),
                node(null, 0.5),
                node("valid", 0.5));
        Map<String, RoadmapResponse.QuestProgress> progress = Map.of(
                "valid", completed("valid"));

        RoadmapProgressCalculator.ProgressCalculation result =
                RoadmapProgressCalculator.calculate(nodes, progress);

        assertEquals(1, result.totalQuests());
        assertEquals(1, result.completedQuests());
        assertEquals(100.0, result.completionPercentage(), DELTA);
    }

    // --- scenario 6: no valid nodes ---

    @Test
    void calculate_noValidNodes_returnsAllZero() {
        List<RoadmapResponse.RoadmapNode> nodes = List.of(
                node("", null),
                node(null, null));
        Map<String, RoadmapResponse.QuestProgress> progress = Map.of();

        RoadmapProgressCalculator.ProgressCalculation result =
                RoadmapProgressCalculator.calculate(nodes, progress);

        assertEquals(0, result.totalQuests());
        assertEquals(0, result.completedQuests());
        assertEquals(0.0, result.completionPercentage(), DELTA);
        assertEquals(0.0, result.completedWeight(), DELTA);
        assertEquals(0.0, result.totalWeight(), DELTA);
    }

    // --- bonus: empty node list ---

    @Test
    void calculate_emptyNodeList_returnsAllZero() {
        RoadmapProgressCalculator.ProgressCalculation result =
                RoadmapProgressCalculator.calculate(List.of(), Map.of());

        assertEquals(0, result.totalQuests());
        assertEquals(0, result.completedQuests());
        assertEquals(0.0, result.completionPercentage(), DELTA);
    }

    // --- weight boundary checks ---

    @Test
    void resolveWeight_importanceOne_returnsMaxWeight() {
        assertEquals(1.0, RoadmapProgressCalculator.resolveWeight(1.0), DELTA);
    }

    @Test
    void resolveWeight_importanceZero_returnsMinWeight() {
        assertEquals(0.4, RoadmapProgressCalculator.resolveWeight(0.0), DELTA);
    }

    @Test
    void resolveWeight_importanceHalf_returnsMidWeight() {
        assertEquals(0.7, RoadmapProgressCalculator.resolveWeight(0.5), DELTA);
    }

    @Test
    void resolveWeight_outOfRangeHigh_clampsToMax() {
        assertEquals(1.0, RoadmapProgressCalculator.resolveWeight(1.5), DELTA);
    }

    @Test
    void resolveWeight_outOfRangeLow_clampsToMin() {
        assertEquals(0.4, RoadmapProgressCalculator.resolveWeight(-0.5), DELTA);
    }
}
